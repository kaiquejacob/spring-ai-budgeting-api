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
        var aiResponse = chatClient
                .prompt()
                .user(userMessage)
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
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST -> normalizeNonActionable(response);
        };
    }

    private TransactionAiResponse normalizeNonActionable(TransactionAiResponse response) {
        return new TransactionAiResponse(
                response.intent(),
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
                message
        );
    }
}