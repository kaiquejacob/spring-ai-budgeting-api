package dio.budgeting.application.assistant;

import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

@Service
public class ProcessTransactionAudioWithRetryUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ProcessTransactionAudioWithRetryUseCase.class);

    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;

    public ProcessTransactionAudioWithRetryUseCase(
            ProcessTransactionAudioUseCase processTransactionAudioUseCase
    ) {
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
    }

    public TransactionAiResponse execute(Resource audio) {
        try {
            return processTransactionAudioUseCase.execute(audio);
        } catch (ResourceAccessException exception) {
            log.warn("Transient AI call failure. Retrying once.", exception);

            try {
                return processTransactionAudioUseCase.execute(audio);
            } catch (ResourceAccessException retryException) {
                throw new AiProcessingException(
                        "AI provider is unavailable after retry.",
                        retryException
                );
            } catch (AiProcessingException retryException) {
                throw retryException;
            } catch (Exception retryException) {
                throw new AiProcessingException(
                        "Failed to process transaction audio.",
                        retryException
                );
            }
        } catch (AiProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProcessingException(
                    "Failed to process transaction audio.",
                    exception
            );
        }
    }
}
