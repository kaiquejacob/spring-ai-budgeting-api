package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;

import java.util.UUID;

public record TransactionRequest(
        String description,
        Category category,
        long amount
) {

    public PersistTransactionInput toInput(UUID userId) {
        return new PersistTransactionInput(
                userId,
                description,
                amount * 100,
                category,
                TransactionSource.MANUAL
        );
    }
}