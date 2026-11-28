package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.PendingTransactionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingTransactionJpaRepository extends CrudRepository<PendingTransactionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingTransactionEntity> findByTokenAndUserId(UUID token, UUID userId);

    @Modifying
    @Query("delete from PendingTransactionEntity proposal where proposal.expiresAt <= :cutoff")
    int deleteExpiredAtOrBefore(@Param("cutoff") Instant cutoff);
}
