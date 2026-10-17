package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.output.TransactionOutput;

import java.util.List;
import java.util.UUID;

public record TransactionAiHttpResponse(
        String intent,
        String message,
        UUID confirmationToken,
        TransactionResponse proposedTransaction,
        TransactionResponse createdTransaction,
        List<TransactionResponse> transactions
) {

    public static TransactionAiHttpResponse pending(
            UUID token,
            TransactionAiResponse response
    ) {
        return new TransactionAiHttpResponse(
                response.intent().name(),
                "Confirme a transação para registrá-la.",
                token,
                new TransactionResponse(
                        null,
                        response.category().name(),
                        response.description(),
                        response.amountInCents() / 100.0
                ),
                null,
                null
        );
    }

    public static TransactionAiHttpResponse created(TransactionOutput output) {
        return new TransactionAiHttpResponse(
                "CREATE_TRANSACTION",
                "Transação registrada com sucesso.",
                null,
                null,
                TransactionResponse.from(output),
                null
        );
    }

    public static TransactionAiHttpResponse listed(List<TransactionOutput> outputs) {
        return new TransactionAiHttpResponse(
                "LIST_TRANSACTIONS",
                "Transações encontradas.",
                null,
                null,
                null,
                outputs.stream().map(TransactionResponse::from).toList()
        );
    }

    public static TransactionAiHttpResponse clarification(TransactionAiResponse response) {
        return new TransactionAiHttpResponse(
                response.intent().name(),
                response.messageToUser(),
                null,
                null,
                null,
                null
        );
    }
}