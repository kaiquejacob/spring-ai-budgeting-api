package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.infrastructure.persistence.entity.PendingTransactionEntity;
import dio.budgeting.infrastructure.persistence.repository.PendingTransactionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class PendingTransactionService {

    private static final int EXPIRATION_SECONDS = 300;

    private final PendingTransactionJpaRepository pendingTransactions;
    private final Clock clock;

    public PendingTransactionService(PendingTransactionJpaRepository pendingTransactions, Clock clock) {
        this.pendingTransactions = pendingTransactions;
        this.clock = clock;
    }

    public UUID save(
            UUID userId,
            TransactionAiResponse proposal
    ) {
        Objects.requireNonNull(userId, "O usuário é obrigatório.");
        Objects.requireNonNull(proposal, "A proposta é obrigatória.");

        var token = UUID.randomUUID();

        pendingTransactions.save(new PendingTransactionEntity(
                token, userId, clock.instant().plusSeconds(EXPIRATION_SECONDS),
                proposal.description(), proposal.amountInCents(), proposal.category(),
                proposal.effectiveType(), proposal.occurredAt()));

        return token;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionAiResponse consume(UUID token, UUID userId) {
        Objects.requireNonNull(token, "O token é obrigatório.");
        Objects.requireNonNull(userId, "O usuário é obrigatório.");

        var pending = pendingTransactions.findByTokenAndUserId(token, userId)
                .orElseThrow(() -> new PendingTransactionException(
                        "Proposta não encontrada ou indisponível para este usuário."));

        if (!clock.instant().isBefore(pending.getExpiresAt())) {
            throw new PendingTransactionException("Proposta expirada.");
        }

        pendingTransactions.delete(pending);
        return new TransactionAiResponse(TransactionIntent.CREATE_TRANSACTION,
                pending.getDescription(), pending.getAmountInCents(), pending.getCategory(),
                null, null, null, pending.getType(), pending.getOccurredAt());
    }
}
