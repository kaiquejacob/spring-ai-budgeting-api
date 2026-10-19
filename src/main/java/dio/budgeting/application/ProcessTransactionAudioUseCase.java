package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ProcessTransactionAudioUseCase {

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private static final Logger log =
            LoggerFactory.getLogger(ProcessTransactionAudioUseCase.class);

    public ProcessTransactionAudioUseCase(
            TranscriptionModel transcriptionModel,
            ChatClient chatClient
    ) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClient;
    }

    public TransactionAiResponse execute(Resource audio) {
        var transcription = transcriptionModel.transcribe(audio);
        return interpret(transcription);
    }

    public TransactionAiResponse interpret(String userMessage) {
        try {
            var aiResponse = chatClient
                    .prompt()
                    .user(userMessage)
                    .call()
                    .entity(TransactionAiResponse.class);

            return validate(aiResponse);
        } catch (NonTransientAiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Falha ao interpretar resposta da IA", exception);

            return new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    null,
                    "Não consegui interpretar a solicitação. Tente novamente com mais detalhes."
            );
        }
    }

    private TransactionAiResponse validate(TransactionAiResponse response) {
        if (response == null || response.intent() == null) {
            return new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    null,
                    "Não consegui entender a solicitação. Tente informar a despesa e o valor."
            );
        }

        return switch (response.intent()) {
            case CREATE_TRANSACTION -> validateCreation(response);
            case LIST_TRANSACTIONS -> validateListing(response);
            case SUM_TRANSACTIONS_BY_CATEGORY -> validateSummary(response);
            case LIST_RECENT_TRANSACTIONS -> normalizeRecentTransactions(response);
            case CONFIRM_PENDING -> response;
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    normalizeNonActionable(response);
        };
    }

    private TransactionAiResponse normalizeNonActionable(TransactionAiResponse response) {
        return new TransactionAiResponse(
                response.intent(),
                null,
                null,
                null,
                null,
                response.messageToUser()
        );
    }

    private TransactionAiResponse validateCreation(TransactionAiResponse response) {
        if (response.description() == null || response.description().isBlank()) {
            return clarification("Informe uma descrição para a transação.");
        }

        if (response.amountInCents() == null || response.amountInCents() <= 0) {
            return clarification("Informe um valor maior que zero.");
        }

        if (response.category() == null) {
            return clarification("Informe uma categoria válida: GROCERIES, PHARMA ou AUTO.");
        }

        return response;
    }

    private TransactionAiResponse validateListing(TransactionAiResponse response) {
        if (response.category() == null) {
            return clarification("Informe a categoria que deseja consultar.");
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

    private TransactionAiResponse validateSummary(TransactionAiResponse response) {
        if (response.category() == null) {
            return clarification("Informe a categoria que deseja consultar.");
        }

        if (response.period() == null) {
            return new TransactionAiResponse(
                    response.intent(),
                    response.description(),
                    response.amountInCents(),
                    response.category(),
                    TransactionAiResponse.AiPeriod.ALL_TIME,
                    response.messageToUser()
            );
        }

        return response;
    }

    private TransactionAiResponse normalizeRecentTransactions(TransactionAiResponse response) {
        return new TransactionAiResponse(
                TransactionIntent.LIST_RECENT_TRANSACTIONS,
                null,
                null,
                null,
                null,
                response.messageToUser()
        );
    }
}