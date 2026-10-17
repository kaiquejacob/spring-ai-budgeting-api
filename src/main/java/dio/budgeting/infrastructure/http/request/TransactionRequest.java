package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;

public record TransactionRequest(
        String description,
        Category category,
        long amount
) {

    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(
                description,
                amount,
                category,
                TransactionSource.MANUAL
        );
    }
}