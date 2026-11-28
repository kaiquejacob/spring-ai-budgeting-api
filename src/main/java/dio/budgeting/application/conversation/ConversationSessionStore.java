package dio.budgeting.application.conversation;

import dio.budgeting.domain.Category;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationSessionStore {

    Optional<StoredSession> findById(UUID id);

    void save(StoredSession session);

    record StoredSession(UUID id, UUID userId, Instant expiresAt, PendingState pendingProposal,
                         UUID pendingConfirmationToken, Category lastCategory, String lastPeriod) {}

    record PendingState(String intent, String description, Long amountInCents, Category category, String period) {}
}
