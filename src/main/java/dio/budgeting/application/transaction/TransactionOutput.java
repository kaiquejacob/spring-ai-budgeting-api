package dio.budgeting.application.transaction;

import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record TransactionOutput(String id, String description, String category, double value,
                                TransactionType type, Instant occurredAt) {
    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                BigDecimal.valueOf(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                transaction.getType(),
                transaction.getOccurredAt());
    }
}
