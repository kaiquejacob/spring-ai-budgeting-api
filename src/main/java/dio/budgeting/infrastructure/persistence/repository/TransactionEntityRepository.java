package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionEntityRepository
        extends CrudRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByUserIdAndCategory(
            UUID userId,
            Category category
    );

    @Query("""
        select coalesce(sum(t.amount), 0)
        from TransactionEntity t
        where t.userId = :userId
          and t.category = :category
          and t.createdAt between :startDate and :endDate
        """)
    long sumAmountByUserIdCategoryAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("category") Category category,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    List<TransactionEntity> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}