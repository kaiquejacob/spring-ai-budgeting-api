package dio.budgeting.application.transaction;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;

import java.util.UUID;

public record PersistTransactionInput(
        UUID userId,
        String description,
        Long amountInCents,
        Category category,
        TransactionSource source
) {
}