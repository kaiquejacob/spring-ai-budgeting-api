package dio.budgeting.application.ai;

import dio.budgeting.domain.Category;

public record TransactionAiResponse(
        TransactionIntent intent,
        String description,
        Long amountInCents,
        Category category,
        AiPeriod period,
        String messageToUser
) {

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