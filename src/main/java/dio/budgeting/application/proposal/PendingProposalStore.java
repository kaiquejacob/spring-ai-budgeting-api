package dio.budgeting.application.proposal;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingProposalStore {

    void save(StoredProposal proposal);

    Optional<StoredProposal> findForUpdate(UUID token, UUID userId);

    void delete(UUID token);

    int deleteExpiredAtOrBefore(Instant cutoff);

    record StoredProposal(UUID token, UUID userId, Instant expiresAt, String description, long amountInCents,
                          Category category, TransactionType type, Instant occurredAt) {}
}
