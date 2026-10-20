package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionEntityRepository transactionEntityRepository;

    public JpaTransactionRepository(
            TransactionEntityRepository transactionEntityRepository
    ) {
        this.transactionEntityRepository = transactionEntityRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var entity = TransactionEntity.from(transaction);

        return transactionEntityRepository
                .save(entity)
                .toDomain();
    }

    @Override
    public List<Transaction> findByUserIdAndCategory(
            UUID userId,
            Category category
    ) {
        return transactionEntityRepository
                .findByUserIdAndCategory(userId, category)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public long sumAmountByUserIdCategoryAndCreatedAtBetween(
            UUID userId,
            Category category,
            Instant startDate,
            Instant endDate
    ) {
        return transactionEntityRepository
                .sumAmountByUserIdCategoryAndCreatedAtBetween(
                        userId,
                        category,
                        startDate,
                        endDate
                );
    }

    @Override
    public List<Transaction> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId) {
        return transactionEntityRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }
}