package dio.budgeting.application.ai;

import dio.budgeting.infrastructure.ai.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ProcessReceiptImageUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ProcessReceiptImageUseCase.class);

    private final GeminiProperties geminiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProcessReceiptImageUseCase(
            GeminiProperties geminiProperties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;

        this.restClient = restClientBuilder
                .baseUrl(geminiProperties.baseUrl())
                .defaultHeader("x-goog-api-key", geminiProperties.apiKey())
                .build();
    }

    public TransactionAiResponse execute(
            Resource image,
            String mimeType
    ) {
        try {
            var normalizedImage = ReceiptImageNormalizer.normalize(image, mimeType);

            log.info(
                    "Processing receipt: mimeType={}, sizeBytes={}",
                    normalizedImage.mimeType(),
                    normalizedImage.bytes().length
            );
            var base64Image = Base64.getEncoder().encodeToString(
                    normalizedImage.bytes()
            );

            var requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of(
                                                    "text",
                                                    """
                                                    Extraia a transação deste recibo.

                                                    Responda apenas com um JSON no formato:
                                                    {
                                                      "intent": "CREATE_TRANSACTION",
                                                      "description": "descrição curta",
                                                      "amountInCents": 8000,
                                                      "category": "GROCERIES",
                                                      "period": null,
                                                      "messageToUser": null
                                                    }

                                                    Regras:
                                                    - Use apenas dados visíveis no recibo.
                                                    - amountInCents deve ser o valor total em centavos.
                                                    - Use apenas GROCERIES, PHARMA ou AUTO.
                                                    - Se não conseguir extrair valor ou categoria, use:
                                                      {"intent": "CLARIFICATION_NEEDED", "messageToUser": "Não foi possível identificar a transação."}
                                                    - Não invente dados.
                                                    """
                                            ),
                                            Map.of(
                                                    "inline_data", Map.of(
                                                            "mime_type", normalizedImage.mimeType(),
                                                            "data", base64Image
                                                    )
                                            )
                                    )
                            )
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json"
                    )
            );

            var response = restClient.post()
                    .uri("/models/{model}:generateContent", geminiProperties.model())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseResponse(response);
        } catch (Exception exception) {
            log.error("Falha ao processar recibo com Gemini", exception);

            throw new AiProcessingException(
                    "Failed to process receipt image.",
                    exception
            );
        }
    }

    private TransactionAiResponse parseResponse(String responseBody) {
        try {
            var root = objectMapper.readTree(responseBody);

            var text = root
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(null);

            if (text == null || text.isBlank()) {
                return clarification(
                        "Não foi possível identificar a transação no recibo."
                );
            }

            var transaction = objectMapper.readValue(
                    text,
                    TransactionAiResponse.class
            );

            return validate(transaction);
        } catch (Exception exception) {
            log.warn("Falha ao interpretar resposta do Gemini", exception);

            return clarification(
                    "Não foi possível interpretar a resposta do recibo."
            );
        }
    }

    private TransactionAiResponse validate(TransactionAiResponse response) {
        if (response == null || response.intent() == null) {
            return clarification(
                    "Não foi possível identificar a transação no recibo."
            );
        }

        if (response.intent() == TransactionIntent.CREATE_TRANSACTION) {
            if (response.description() == null
                    || response.amountInCents() == null
                    || response.amountInCents() <= 0
                    || response.category() == null) {
                return clarification(
                        "Não foi possível extrair todos os dados do recibo."
                );
            }
        }

        return response;
    }

    private TransactionAiResponse clarification(String message) {
        return new TransactionAiResponse(
                TransactionIntent.CLARIFICATION_NEEDED,
                null,
                null,
                null,
                null,
                message
        );
    }
}