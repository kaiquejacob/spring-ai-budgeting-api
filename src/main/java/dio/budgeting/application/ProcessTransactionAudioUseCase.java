package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ProcessTransactionAudioUseCase {

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    public ProcessTransactionAudioUseCase(
            TranscriptionModel transcriptionModel,
            ChatClient chatClient,
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase
    ) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClient;
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
    }

    public TransactionAiResponse execute(Resource audio) {
        var transcription = transcriptionModel.transcribe(audio);

        var aiResponse = chatClient
                .prompt()
                .user(transcription)
                .call()
                .entity(TransactionAiResponse.class);

        return validate(aiResponse);
    }

    private TransactionAiResponse validate(TransactionAiResponse response) {
        if (response == null || response.intent() == null) {
            return new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    "Não consegui entender a solicitação. Tente informar a despesa e o valor."
            );
        }

        return switch (response.intent()) {
            case CREATE_TRANSACTION -> validateCreation(response);
            case LIST_TRANSACTIONS -> validateListing(response);
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST -> response;
        };
    }

    private TransactionAiResponse validateCreation(TransactionAiResponse response) {
        var description = response.description();
        var amount = response.amountInCents();
        var category = response.category();

        if (description == null || description.isBlank()) {
            return clarification("Informe uma descrição para a transação.");
        }

        if (amount == null || amount <= 0) {
            return clarification("Informe um valor maior que zero.");
        }

        if (category == null) {
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
                message
        );
    }
}