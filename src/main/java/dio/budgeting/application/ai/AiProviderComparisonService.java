package dio.budgeting.application.ai;

import dio.budgeting.application.ProcessTransactionAudioUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiProviderComparisonService {

    private static final Logger log =
            LoggerFactory.getLogger(AiProviderComparisonService.class);

    private final ProcessTransactionAudioUseCase groqUseCase;
    private final GeminiTextService geminiTextService;

    public AiProviderComparisonService(
            ProcessTransactionAudioUseCase groqUseCase,
            GeminiTextService geminiTextService
    ) {
        this.groqUseCase = groqUseCase;
        this.geminiTextService = geminiTextService;
    }

    public ComparisonResult compare(String userMessage) {
        var groqStart = System.nanoTime();
        var groqResponse = executeSafely(
                "Groq",
                () -> groqUseCase.interpret(userMessage)
        );
        var groqDuration = elapsedMs(groqStart);

        var geminiStart = System.nanoTime();
        var geminiResponse = executeSafely(
                "Gemini",
                () -> geminiTextService.interpret(userMessage)
        );
        var geminiDuration = elapsedMs(geminiStart);

        var result = new ComparisonResult(
                userMessage,
                groqResponse,
                groqDuration,
                geminiResponse,
                geminiDuration
        );

        log.info("AI provider comparison: {}", result);

        return result;
    }

    private TransactionAiResponse executeSafely(
            String provider,
            ProviderCall call
    ) {
        try {
            return call.execute();
        } catch (Exception exception) {
            log.warn("Falha ao consultar o provedor {}", provider, exception);

            return new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    null,
                    "Falha ao processar a solicitação no provedor " + provider + ".",
                    "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @FunctionalInterface
    private interface ProviderCall {
        TransactionAiResponse execute();
    }

    public record ComparisonResult(
            String userMessage,
            TransactionAiResponse groqResponse,
            long groqDurationMs,
            TransactionAiResponse geminiResponse,
            long geminiDurationMs
    ) {
    }
}