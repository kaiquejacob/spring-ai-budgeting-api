package dio.budgeting.application.assistant;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.ai.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;

@Service
public class ProcessReceiptImageUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ProcessReceiptImageUseCase.class);

    private final GeminiProperties geminiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProcessReceiptImageUseCase(
            GeminiProperties geminiProperties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;

        this.restClient = restClientBuilder
                .baseUrl(geminiProperties.baseUrl())
                .defaultHeader("x-goog-api-key", geminiProperties.apiKey())
                .build();
    }

    public TransactionAiResponse execute(
            Resource image,
            String mimeType
    ) {
        var reference = ZonedDateTime.now(clock);
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
                                                    - Use apenas %s.
                                                    - Para CREATE_TRANSACTION, inclua type (INCOME ou EXPENSE) e occurredOn
                                                      (data civil no formato yyyy-MM-dd) quando houver evidência para cada campo.
                                                    - Use %s como data local de referência para resolver datas relativas.
                                                    - Omita type ou occurredOn quando a informação correspondente não estiver presente.
                                                    - Se não conseguir extrair valor ou categoria, use:
                                                      {"intent": "CLARIFICATION_NEEDED", "messageToUser": "Não foi possível identificar a transação."}
                                                    - Não invente dados.
                                                    """.formatted(Category.allowedNamesInPortuguese(), reference.toLocalDate())
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

            return parseResponse(response, reference);
        } catch (Exception exception) {
            log.error("Falha ao processar recibo com Gemini", exception);

            throw new AiProcessingException(
                    "Failed to process receipt image.",
                    exception
            );
        }
    }

    private TransactionAiResponse parseResponse(String responseBody, ZonedDateTime reference) {
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
                    AiTransactionExtraction.class
            );

            return validate(transaction == null ? null : transaction.toResponse(reference));
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
                message,
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }
}
