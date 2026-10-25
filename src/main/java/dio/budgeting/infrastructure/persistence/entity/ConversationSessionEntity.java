package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.Category;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_sessions")
public class ConversationSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private String pendingIntent;

    private String pendingDescription;

    private Long pendingAmountInCents;

    @Enumerated(EnumType.STRING)
    private Category pendingCategory;

    private String pendingPeriod;

    private UUID pendingConfirmationToken;

    private Category lastCategory;

    private String lastPeriod;

    @Column(nullable = false)
    private Instant expiresAt;

    public ConversationSessionEntity() {
    }

    public ConversationSessionEntity(
            UUID id,
            UUID userId,
            Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPendingIntent() {
        return pendingIntent;
    }

    public void setPendingIntent(String pendingIntent) {
        this.pendingIntent = pendingIntent;
    }

    public String getPendingDescription() {
        return pendingDescription;
    }

    public void setPendingDescription(String pendingDescription) {
        this.pendingDescription = pendingDescription;
    }

    public Long getPendingAmountInCents() {
        return pendingAmountInCents;
    }

    public void setPendingAmountInCents(Long pendingAmountInCents) {
        this.pendingAmountInCents = pendingAmountInCents;
    }

    public Category getPendingCategory() {
        return pendingCategory;
    }

    public void setPendingCategory(Category pendingCategory) {
        this.pendingCategory = pendingCategory;
    }

    public String getPendingPeriod() {
        return pendingPeriod;
    }

    public void setPendingPeriod(String pendingPeriod) {
        this.pendingPeriod = pendingPeriod;
    }

    public Category getLastCategory() {
        return lastCategory;
    }

    public void setLastCategory(Category lastCategory) {
        this.lastCategory = lastCategory;
    }

    public String getLastPeriod() {
        return lastPeriod;
    }

    public void setLastPeriod(String lastPeriod) {
        this.lastPeriod = lastPeriod;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getPendingConfirmationToken() {
        return pendingConfirmationToken;
    }

    public void setPendingConfirmationToken(UUID pendingConfirmationToken) {
        this.pendingConfirmationToken = pendingConfirmationToken;
    }
}