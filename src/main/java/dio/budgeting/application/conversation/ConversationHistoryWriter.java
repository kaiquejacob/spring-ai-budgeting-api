package dio.budgeting.application.conversation;

import java.time.Instant;
import java.util.UUID;

public interface ConversationHistoryWriter {

    void append(UUID conversationId, String question, String answer, Instant exchangedAt);
}
