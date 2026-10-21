package dio.budgeting.application.ai;


import dio.budgeting.infrastructure.ai.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class GeminiTextService {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiTextService.class);

    private final GeminiProperties geminiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiTextService(
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

    public TransactionAiResponse interpret(String userMessage) {
        var maxAttempts = 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var requestBody = Map.of(
                        "contents", List.of(
                                Map.of(
                                        "parts", List.of(
                                                Map.of("text", userMessage)
                                        )
                                )
                        ),
                        "generationConfig", Map.of(
                                "responseMimeType", "application/json"
                        )
                );

                var response = restClient.post()
                        .uri("/models/{model}:generateContent",
                                geminiProperties.model())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                return parseResponse(response);
            } catch (org.springframework.web.client.HttpServerErrorException
                             .ServiceUnavailable exception) {

                log.warn(
                        "Gemini indisponível na tentativa {} de {}",
                        attempt,
                        maxAttempts,
                        exception
                );

                if (attempt == maxAttempts) {
                    return clarification(
                            "O Gemini está temporariamente indisponível. Tente novamente em instantes."
                    );
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    return clarification(
                            "A solicitação foi interrompida."
                    );
                }
            } catch (Exception exception) {
                log.error("Falha ao interpretar mensagem com Gemini", exception);

                return clarification(
                        "Não foi possível processar a solicitação com o Gemini."
                );
            }
        }

        return clarification(
                "O Gemini está temporariamente indisponível."
        );
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
                        "O Gemini não retornou uma interpretação válida."
                );
            }

            return objectMapper.readValue(
                    text,
                    TransactionAiResponse.class
            );
        } catch (Exception exception) {
            log.warn("Falha ao interpretar resposta do Gemini", exception);

            return clarification(
                    "Não foi possível interpretar a resposta do Gemini."
            );
        }
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