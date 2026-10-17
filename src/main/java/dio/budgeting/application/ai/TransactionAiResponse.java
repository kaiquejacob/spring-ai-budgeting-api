package dio.budgeting.application.ai;

import dio.budgeting.domain.Category;

public record TransactionAiResponse(
        TransactionIntent intent,
        String description,
        Long amountInCents,
        Category category,
        String messageToUser
) {
}