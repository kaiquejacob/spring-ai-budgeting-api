package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;

import java.util.List;
import java.util.UUID;

public sealed interface AiIntentResult
        permits AiIntentResult.Pending,
        AiIntentResult.Created,
        AiIntentResult.Clarification,
        AiIntentResult.Listed,
        AiIntentResult.Summarized,
        AiIntentResult.Recent {

    UUID sessionId();

    record Pending(
            UUID token,
            TransactionAiResponse proposal,
            UUID sessionId
    ) implements AiIntentResult {
    }

    record Created(
            TransactionOutput output,
            UUID sessionId
    ) implements AiIntentResult {
    }

    record Clarification(
            TransactionAiResponse response,
            UUID sessionId
    ) implements AiIntentResult {
    }

    record Listed(
            List<TransactionOutput> transactions,
            UUID sessionId
    ) implements AiIntentResult {
    }

    record Summarized(
            Category category,
            TransactionAiResponse.AiPeriod period,
            long totalInCents,
            UUID sessionId
    ) implements AiIntentResult {
    }

    record Recent(
            List<TransactionOutput> transactions,
            UUID sessionId
    ) implements AiIntentResult {
    }
}
