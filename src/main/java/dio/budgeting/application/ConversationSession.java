package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;

import java.time.Instant;
import java.util.UUID;

public class ConversationSession {

    private final UUID id;
    private TransactionAiResponse pendingProposal;
    private Category lastCategory;
    private TransactionAiResponse.AiPeriod lastPeriod;
    private Instant expiresAt;

    public ConversationSession(UUID id) {
        this.id = id;
        this.expiresAt = Instant.now().plusSeconds(900);
    }

    public UUID id() {
        return id;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void renew() {
        this.expiresAt = Instant.now().plusSeconds(900);
    }

    public TransactionAiResponse pendingProposal() {
        return pendingProposal;
    }

    public void setPendingProposal(TransactionAiResponse pendingProposal) {
        this.pendingProposal = pendingProposal;
    }

    public void clearPendingProposal() {
        this.pendingProposal = null;
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
}