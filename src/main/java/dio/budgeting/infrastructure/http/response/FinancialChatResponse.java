package dio.budgeting.infrastructure.http.response;

import java.util.UUID;

public record FinancialChatResponse(
        String answer,
        UUID sessionId
) {
}