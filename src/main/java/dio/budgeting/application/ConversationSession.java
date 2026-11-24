package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

public class ConversationSession {

    private final UUID id;
    private final UUID userId;
    private TransactionAiResponse pendingProposal;
    private UUID pendingConfirmationToken;
    private Category lastCategory;
    private TransactionAiResponse.AiPeriod lastPeriod;
    private Instant expiresAt;

    public ConversationSession(UUID id, UUID userId, Clock clock) {
        this.id = id;
        this.userId = userId;
        this.expiresAt = clock.instant().plusSeconds(900);
        this.clock = clock;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    private final Clock clock;

    public boolean isExpired() {
        return clock.instant().isAfter(expiresAt);
    }

    public void renew() {
        this.expiresAt = clock.instant().plusSeconds(900);
    }

    public TransactionAiResponse pendingProposal() {
        return pendingProposal;
    }

    public void setPendingProposal(TransactionAiResponse pendingProposal) {
        this.pendingProposal = pendingProposal;
    }

    public void clearPendingProposal() {
        this.pendingProposal = null;
        this.pendingConfirmationToken = null;
    }

    public Category lastCategory() {
        return lastCategory;
    }

    public void setLastCategory(Category lastCategory) {
        this.lastCategory = lastCategory;
    }

    public TransactionAiResponse.AiPeriod lastPeriod() {
        return lastPeriod;
    }

    public void setLastPeriod(TransactionAiResponse.AiPeriod lastPeriod) {
        this.lastPeriod = lastPeriod;
    }

    public UUID pendingConfirmationToken() {
        return pendingConfirmationToken;
    }

    public void setPendingConfirmationToken(UUID pendingConfirmationToken) {
        this.pendingConfirmationToken = pendingConfirmationToken;
    }
}