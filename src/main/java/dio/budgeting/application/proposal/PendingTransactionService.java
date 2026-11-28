package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.proposal.PendingProposalStore.StoredProposal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class PendingTransactionService {

    private static final int EXPIRATION_SECONDS = 300;

    private final PendingProposalStore pendingTransactions;
    private final Clock clock;

    public PendingTransactionService(PendingProposalStore pendingTransactions, Clock clock) {
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

        pendingTransactions.save(new StoredProposal(
                token, userId, clock.instant().plusSeconds(EXPIRATION_SECONDS),
                proposal.description(), proposal.amountInCents(), proposal.category(),
                proposal.effectiveType(), proposal.occurredAt()));

        return token;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionAiResponse consume(UUID token, UUID userId) {
        Objects.requireNonNull(token, "O token é obrigatório.");
        Objects.requireNonNull(userId, "O usuário é obrigatório.");

        var pending = pendingTransactions.findForUpdate(token, userId)
                .orElseThrow(() -> new PendingTransactionException(
                        "Proposta não encontrada ou indisponível para este usuário."));

        if (!clock.instant().isBefore(pending.expiresAt())) {
            throw new PendingTransactionException("Proposta expirada.");
        }

        pendingTransactions.delete(pending.token());
        return new TransactionAiResponse(TransactionIntent.CREATE_TRANSACTION,
                pending.description(), pending.amountInCents(), pending.category(),
                null, null, null, pending.type(), pending.occurredAt());
    }
}
