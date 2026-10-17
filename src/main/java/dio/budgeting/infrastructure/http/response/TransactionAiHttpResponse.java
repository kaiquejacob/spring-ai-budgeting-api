package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.output.TransactionOutput;

import java.util.List;

public record TransactionAiHttpResponse(
        String intent,
        String message,
        TransactionResponse createdTransaction,
        List<TransactionResponse> transactions
) {

    public static TransactionAiHttpResponse from(TransactionAiResponse response) {
        return new TransactionAiHttpResponse(
                response.intent().name(),
                response.messageToUser(),
                null,
                null
        );
    }

    public static TransactionAiHttpResponse created(TransactionOutput output) {
        return new TransactionAiHttpResponse(
                "CREATE_TRANSACTION",
                "Transação registrada com sucesso.",
                TransactionResponse.from(output),
                null
        );
    }

    public static TransactionAiHttpResponse listed(List<TransactionOutput> outputs) {
        return new TransactionAiHttpResponse(
                "LIST_TRANSACTIONS",
                "Transações encontradas.",
                null,
                outputs.stream().map(TransactionResponse::from).toList()
        );
    }
}