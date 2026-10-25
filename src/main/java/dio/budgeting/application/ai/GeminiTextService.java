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

    private static final String GEMINI_INTERPRETATION_PROMPT = """
            Você é um assistente de um sistema de gestão financeira.
            
            Sua tarefa é interpretar a solicitação do usuário e responder EXCLUSIVAMENTE com um objeto JSON válido.
            
            Não inclua explicações, Markdown, crases ou texto fora do JSON.
            
            Use exatamente este formato:
            
            {
              "intent": "VALOR_DO_ENUM",
              "description": "texto opcional",
              "amountInCents": 0,
              "category": "VALOR_DO_ENUM_OU_NULL",
              "period": "VALOR_DO_ENUM_OU_NULL",
              "messageToUser": "texto opcional"
            }
            
            Regras obrigatórias:
            
            1. O campo "intent" deve ser exatamente um destes valores:
            - CLARIFICATION_NEEDED
            - UNSUPPORTED_REQUEST
            - LIST_TRANSACTIONS
            - LIST_RECENT_TRANSACTIONS
            - CREATE_TRANSACTION
            - SUM_TRANSACTIONS_BY_CATEGORY
            - CONFIRM_PENDING
            
            2. Para perguntas sobre quanto foi gasto em uma categoria, use:
            "intent": "SUM_TRANSACTIONS_BY_CATEGORY"
            
            3. Para "mercado", use:
            "category": "GROCERIES"
            
            4. Para "este mês", use:
            "period": "CURRENT_MONTH"
            
            5. Não invente novos valores de intent, category ou period.
            
            6. O campo de mensagem para o usuário chama-se exatamente "messageToUser".
            Não use "message".
            
            7. Se faltar informação essencial, use:
            "intent": "CLARIFICATION_NEEDED"
            e explique em "messageToUser".
            
            8. Não realize cálculos nem consulte dados financeiros.
            Apenas classifique a solicitação.
            
            Mensagem do usuário:
            """;

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
                                                Map.of(
                                                        "text",
                                                        GEMINI_INTERPRETATION_PROMPT
                                                                + "\n\n"
                                                                + userMessage
                                                )
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

            log.info("Gemini raw response text: {}", text);

            if (text == null || text.isBlank()) {
                return clarification(
                        "O Gemini não retornou uma interpretação válida."
                );
            }

            var json = extractJson(text);

            return objectMapper.readValue(
                    json,
                    TransactionAiResponse.class
            );
        } catch (Exception exception) {
            log.warn("Falha ao interpretar resposta do Gemini", exception);

            return clarification(
                    "Não foi possível interpretar a resposta do Gemini."
            );
        }
    }

    private String extractJson(String text) {
        var content = text.trim();

        if (content.startsWith("```")) {
            content = content.replaceAll("^```(?:json)?\\s*", "");
            content = content.replaceAll("\\s*```$", "");
        }

        var jsonStart = content.indexOf("{");
        var jsonEnd = content.lastIndexOf("}");

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            content = content.substring(jsonStart, jsonEnd + 1);
        }

        return content.trim();
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