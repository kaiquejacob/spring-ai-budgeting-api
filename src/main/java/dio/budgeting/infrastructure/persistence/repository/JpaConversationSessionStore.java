package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.application.conversation.ConversationSessionStore;
import dio.budgeting.infrastructure.persistence.entity.ConversationSessionEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaConversationSessionStore implements ConversationSessionStore {

    private final ConversationSessionJpaRepository sessions;

    public JpaConversationSessionStore(ConversationSessionJpaRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<StoredSession> findById(UUID id) {
        return sessions.findById(id).map(entity -> {
            var proposal = new PendingState(entity.getPendingIntent(), entity.getPendingDescription(),
                    entity.getPendingAmountInCents(), entity.getPendingCategory(), entity.getPendingPeriod());
            return new StoredSession(entity.getId(), entity.getUserId(), entity.getExpiresAt(), proposal,
                    entity.getPendingConfirmationToken(), entity.getLastCategory(), entity.getLastPeriod());
        });
    }

    @Override
    public void save(StoredSession session) {
        var entity = new ConversationSessionEntity(session.id(), session.userId(), session.expiresAt());
        var proposal = session.pendingProposal();
        if (proposal != null) {
            entity.setPendingIntent(proposal.intent());
            entity.setPendingDescription(proposal.description());
            entity.setPendingAmountInCents(proposal.amountInCents());
            entity.setPendingCategory(proposal.category());
            entity.setPendingPeriod(proposal.period());
        }
        entity.setPendingConfirmationToken(session.pendingConfirmationToken());
        entity.setLastCategory(session.lastCategory());
        entity.setLastPeriod(session.lastPeriod());
        sessions.save(entity);
    }
}
