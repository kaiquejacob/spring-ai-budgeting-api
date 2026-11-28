package dio.budgeting.application.conversation;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionAiResponse.AiPeriod;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.conversation.ConversationSessionStore.PendingState;
import dio.budgeting.application.conversation.ConversationSessionStore.StoredSession;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ConversationSessionService {

    private static final long SESSION_DURATION_SECONDS = 900;

    private final ConversationSessionStore repository;
    private final Clock clock;

    public ConversationSessionService(ConversationSessionStore repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ConversationSession getOrCreate(UUID sessionId, UUID userId) {
        var stored = sessionId == null ? null : repository.findById(sessionId).orElse(null);
        if (stored != null) {
            validateOwner(stored, userId);
        }

        if (stored == null || isExpired(stored)) {
            stored = new StoredSession(UUID.randomUUID(), userId, nextExpiration(), null, null, null, null);
        } else {
            stored = new StoredSession(stored.id(), stored.userId(), nextExpiration(), stored.pendingProposal(),
                    stored.pendingConfirmationToken(), stored.lastCategory(), stored.lastPeriod());
        }
        repository.save(stored);

        var session = new ConversationSession(stored.id(), stored.userId(), clock);
        var proposal = stored.pendingProposal();
        if (proposal != null && proposal.intent() != null) {
            session.setPendingProposal(new TransactionAiResponse(
                    TransactionIntent.valueOf(proposal.intent()), proposal.description(), proposal.amountInCents(),
                    proposal.category(), parsePeriod(proposal.period()), null,
                    "Não há uma proposta válida para confirmar. Gere uma nova proposta."));
            session.setPendingConfirmationToken(stored.pendingConfirmationToken());
        }
        session.setLastCategory(stored.lastCategory());
        session.setLastPeriod(parsePeriod(stored.lastPeriod()));
        return session;
    }

    public void save(ConversationSession session) {
        var stored = repository.findById(session.id())
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada."));

        validateOwner(stored, session.userId());
        if (isExpired(stored)) {
            throw new IllegalArgumentException("A sessão expirou. Inicie uma nova conversa.");
        }

        var proposal = session.pendingProposal();
        var pending = proposal == null ? null : new PendingState(proposal.intent().name(), proposal.description(),
                proposal.amountInCents(), proposal.category(), periodName(proposal.period()));
        repository.save(new StoredSession(session.id(), session.userId(), nextExpiration(), pending,
                proposal == null ? null : session.pendingConfirmationToken(),
                session.lastCategory(), periodName(session.lastPeriod())));
    }

    private static void validateOwner(StoredSession session, UUID userId) {
        if (!session.userId().equals(userId)) {
            throw new SessionAccessDeniedException();
        }
    }

    private boolean isExpired(StoredSession session) {
        return clock.instant().isAfter(session.expiresAt());
    }

    private Instant nextExpiration() {
        return clock.instant().plusSeconds(SESSION_DURATION_SECONDS);
    }

    private static AiPeriod parsePeriod(String value) {
        return value == null ? null : AiPeriod.valueOf(value);
    }

    private static String periodName(AiPeriod period) {
        return period == null ? null : period.name();
    }
}
