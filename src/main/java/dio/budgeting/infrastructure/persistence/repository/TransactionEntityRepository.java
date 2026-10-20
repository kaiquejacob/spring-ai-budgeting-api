package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionEntityRepository
        extends CrudRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findAllByCategory(Category category);

    List<TransactionEntity> findAllByCategoryAndCreatedAtBetween(
            Category category,
            Instant start,
            Instant end
    );

    List<TransactionEntity> findTop10ByOrderByCreatedAtDesc();

    @Query("""
        select coalesce(sum(t.amount), 0)
        from TransactionEntity t
        where t.category = :category
          and t.createdAt between :startDate and :endDate
        """)
    long sumAmountByCategoryAndDateBetween(
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}