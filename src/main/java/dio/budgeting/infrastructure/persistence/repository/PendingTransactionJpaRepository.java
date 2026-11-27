package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.PendingTransactionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface PendingTransactionJpaRepository extends CrudRepository<PendingTransactionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingTransactionEntity> findByTokenAndUserId(UUID token, UUID userId);
}
