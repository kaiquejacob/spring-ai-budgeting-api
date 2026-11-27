package dio.budgeting.application.transaction;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import dio.budgeting.domain.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record PersistTransactionInput(
        UUID userId,
        String description,
        Long amountInCents,
        Category category,
        TransactionSource source,
        TransactionType type,
        Instant occurredAt
) {
    public PersistTransactionInput(UUID userId, String description, Long amountInCents,
                                   Category category, TransactionSource source) {
        this(userId, description, amountInCents, category, source, null, null);
    }
}
