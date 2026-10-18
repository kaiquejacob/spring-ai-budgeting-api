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
        CURRENT_MONTH,
        ALL_TIME
    }
}