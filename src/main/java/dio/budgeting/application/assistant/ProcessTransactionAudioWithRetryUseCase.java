package dio.budgeting.application.assistant;

import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.ZonedDateTime;

@Service
public class ProcessTransactionAudioWithRetryUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ProcessTransactionAudioWithRetryUseCase.class);

    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;
    private final Clock clock;

    public ProcessTransactionAudioWithRetryUseCase(
            ProcessTransactionAudioUseCase processTransactionAudioUseCase,
            Clock clock
    ) {
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
        this.clock = clock;
    }

    public TransactionAiResponse execute(Resource audio) {
        var reference = ZonedDateTime.now(clock);
        try {
            return processTransactionAudioUseCase.execute(audio, reference);
        } catch (ResourceAccessException exception) {
            log.warn("Transient AI call failure. Retrying once.", exception);

            try {
                return processTransactionAudioUseCase.execute(audio, reference);
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
