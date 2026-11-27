package dio.budgeting.application.assistant;

import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.domain.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.function.Supplier;

@Service
public class ProcessTransactionAudioUseCase {

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private static final Logger log =
            LoggerFactory.getLogger(ProcessTransactionAudioUseCase.class);

    public ProcessTransactionAudioUseCase(
            TranscriptionModel transcriptionModel,
            @Qualifier("financialChatClient") ChatClient chatClient
    ) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClient;
    }

    public TransactionAiResponse execute(Resource audio, ZonedDateTime reference) {
        var transcription = transcriptionModel.transcribe(audio);
        return interpret(() -> {
            var extraction = chatClient.prompt()
                    .user("""
                            Data local de referência: %s.
                            Somente para CREATE_TRANSACTION, inclua type (INCOME ou EXPENSE) e occurredOn
                            (data civil no formato yyyy-MM-dd) quando houver evidência para cada campo.
                            Resolva datas relativas, como "ontem", a partir da data local de referência.
                            Omita cada campo cuja informação não estiver presente. Não invente tipo nem data.
                            Para as demais intenções, omita type e occurredOn e siga as regras de consulta.

                            Transcrição:
                            %s
                            """.formatted(reference.toLocalDate(), transcription))
                    .call()
                    .entity(AiTransactionExtraction.class);
            return extraction == null ? null : extraction.toResponse(reference);
        });
    }

    public TransactionAiResponse interpret(String userMessage) {
        return interpret(() -> chatClient.prompt()
                .user(userMessage)
                .call()
                .entity(TransactionAiResponse.class));
    }

    private TransactionAiResponse interpret(Supplier<TransactionAiResponse> request) {
        try {
            return validate(request.get());
        } catch (
                NonTransientAiException
                | org.springframework.web.client.ResourceAccessException
                | AiProcessingException exception
        ) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Falha ao interpretar resposta da IA", exception);

            return clarification(
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
                    "Não consegui entender a solicitação. Tente informar a despesa e o valor.",
                    "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
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
                response.messageToUser(),
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }

    private TransactionAiResponse validateCreation(TransactionAiResponse response) {
        if (response.description() == null || response.description().isBlank()) {
            return clarification("Informe uma descrição para a transação.");
        }

        if (response.amountInCents() == null || response.amountInCents() <= 0) {
            return clarification("Informe um valor maior que zero.");
        }

        if (response.category() == null) {
            return clarification("Informe uma categoria válida: " + Category.allowedNamesInPortuguese() + ".");
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
                message,
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }

    private TransactionAiResponse validateSummary(TransactionAiResponse response) {
        if (response.category() == null) {
            return clarification("Informe a categoria que deseja consultar.");
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
                response.messageToUser(),
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }
}
