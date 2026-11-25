package dio.budgeting.application;

import java.util.UUID;

public record FinancialChatResult(String answer, UUID sessionId) {
}
