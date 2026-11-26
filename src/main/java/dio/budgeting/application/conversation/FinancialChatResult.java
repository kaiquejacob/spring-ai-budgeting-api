package dio.budgeting.application.conversation;

import java.util.UUID;

public record FinancialChatResult(String answer, UUID sessionId) {
}
