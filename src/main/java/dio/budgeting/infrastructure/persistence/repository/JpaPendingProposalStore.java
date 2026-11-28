package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.application.proposal.PendingProposalStore;
import dio.budgeting.infrastructure.persistence.entity.PendingTransactionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPendingProposalStore implements PendingProposalStore {

    private final PendingTransactionJpaRepository proposals;

    public JpaPendingProposalStore(PendingTransactionJpaRepository proposals) {
        this.proposals = proposals;
    }

    @Override
    public void save(StoredProposal proposal) {
        proposals.save(new PendingTransactionEntity(proposal.token(), proposal.userId(), proposal.expiresAt(),
                proposal.description(), proposal.amountInCents(), proposal.category(), proposal.type(),
                proposal.occurredAt()));
    }

    @Override
    public Optional<StoredProposal> findForUpdate(UUID token, UUID userId) {
        return proposals.findByTokenAndUserId(token, userId)
                .map(entity -> new StoredProposal(entity.getToken(), entity.getUserId(), entity.getExpiresAt(),
                        entity.getDescription(), entity.getAmountInCents(), entity.getCategory(),
                        entity.getType(), entity.getOccurredAt()));
    }

    @Override
    public void delete(UUID token) {
        proposals.deleteById(token);
    }

    @Override
    public int deleteExpiredAtOrBefore(Instant cutoff) {
        return proposals.deleteExpiredAtOrBefore(cutoff);
    }
}
