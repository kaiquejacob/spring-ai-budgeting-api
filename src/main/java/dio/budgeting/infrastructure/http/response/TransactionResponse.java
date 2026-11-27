package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.transaction.TransactionOutput;
import dio.budgeting.domain.TransactionType;

import java.time.Instant;

public record TransactionResponse(String id, String category, String description, double amount,
                                  TransactionType type, Instant occurredAt) {
    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(output.id(), output.category(), output.description(), output.value(),
                output.type(), output.occurredAt());
    }
}
