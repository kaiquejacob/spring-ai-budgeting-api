package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.infrastructure.persistence.entity.ConversationSessionEntity;
import dio.budgeting.infrastructure.persistence.repository.ConversationSessionJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConversationSessionService {

    private static final long SESSION_DURATION_SECONDS = 900;

    private final ConversationSessionJpaRepository repository;

    public ConversationSessionService(
            ConversationSessionJpaRepository repository
    ) {
        this.repository = repository;
    }

    public ConversationSession getOrCreate(UUID sessionId, UUID userId) {
        var entity = sessionId == null
                ? null
                : repository.findById(sessionId).orElse(null);

        if (entity != null) {
            validateOwner(entity, userId);
        }

        if (entity == null || isExpired(entity)) {
            entity = new ConversationSessionEntity(
                    UUID.randomUUID(),
                    userId,
                    Instant.now().plusSeconds(SESSION_DURATION_SECONDS)
            );
        }

        entity.setExpiresAt(
                Instant.now().plusSeconds(SESSION_DURATION_SECONDS)
        );

        repository.save(entity);

        return toSession(entity);
    }

    public void save(ConversationSession session) {
        var entity = repository.findById(session.id())
                .orElseThrow(() ->
                        new IllegalArgumentException("Sessão não encontrada.")
                );

        validateOwner(entity, session.userId());

        if (isExpired(entity)) {
            throw new IllegalArgumentException(
                    "A sessão expirou. Inicie uma nova conversa."
            );
        }

        entity.setExpiresAt(
                Instant.now().plusSeconds(SESSION_DURATION_SECONDS)
        );

        if (session.pendingProposal() == null) {
            entity.setPendingIntent(null);
            entity.setPendingDescription(null);
            entity.setPendingAmountInCents(null);
            entity.setPendingCategory(null);
            entity.setPendingPeriod(null);
        } else {
            var proposal = session.pendingProposal();

            entity.setPendingIntent(proposal.intent().name());
            entity.setPendingDescription(proposal.description());
            entity.setPendingAmountInCents(proposal.amountInCents());
            entity.setPendingCategory(proposal.category());
            entity.setPendingPeriod(
                    proposal.period() == null
                            ? null
                            : proposal.period().name()
            );
        }

        entity.setLastCategory(session.lastCategory());
        entity.setLastPeriod(
                session.lastPeriod() == null
                        ? null
                        : session.lastPeriod().name()
        );

        repository.save(entity);
    }

    private void validateOwner(
            ConversationSessionEntity entity,
            UUID userId
    ) {
        if (!entity.getUserId().equals(userId)) {
            throw new SessionAccessDeniedException();
        }
    }

    private boolean isExpired(ConversationSessionEntity entity) {
        return Instant.now().isAfter(entity.getExpiresAt());
    }

    private ConversationSession toSession(ConversationSessionEntity entity) {
        var session = new ConversationSession(
                entity.getId(),
                entity.getUserId()
        );

        if (entity.getPendingIntent() != null) {
            session.setPendingProposal(new TransactionAiResponse(
                    TransactionIntent.valueOf(entity.getPendingIntent()),
                    entity.getPendingDescription(),
                    entity.getPendingAmountInCents(),
                    entity.getPendingCategory(),
                    entity.getPendingPeriod() == null
                            ? null
                            : TransactionAiResponse.AiPeriod.valueOf(
                            entity.getPendingPeriod()
                    ),
                    null
            ));
        }

        session.setLastCategory(entity.getLastCategory());
        session.setLastPeriod(
                entity.getLastPeriod() == null
                        ? null
                        : TransactionAiResponse.AiPeriod.valueOf(
                        entity.getLastPeriod()
                )
        );

        return session;
    }
}