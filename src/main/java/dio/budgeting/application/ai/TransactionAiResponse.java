package dio.budgeting.application.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;

import java.time.Instant;
import java.util.Objects;

public record TransactionAiResponse(
        TransactionIntent intent,
        String description,
        Long amountInCents,
        Category category,
        AiPeriod period,
        String messageToUser,
        String s,
        @JsonIgnore TransactionType type,
        @JsonIgnore Instant occurredAt) {

    public TransactionAiResponse(TransactionIntent intent, String description, Long amountInCents,
                                 Category category, AiPeriod period, String messageToUser, String s) {
        this(intent, description, amountInCents, category, period, messageToUser, s, null, null);
    }

    public TransactionType effectiveType() {
        return Objects.requireNonNullElse(type, TransactionType.EXPENSE);
    }

    public enum AiPeriod {
        TODAY,
        YESTERDAY,
        THIS_WEEK,
        LAST_WEEK,
        CURRENT_MONTH,
        LAST_MONTH,
        ALL_TIME
    }
}
