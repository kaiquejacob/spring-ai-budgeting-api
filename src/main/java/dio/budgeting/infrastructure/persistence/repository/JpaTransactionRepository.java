package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionEntityRepository transactionEntityRepository;

    public JpaTransactionRepository(TransactionEntityRepository transactionEntityRepository) {
        this.transactionEntityRepository = transactionEntityRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var entity = TransactionEntity.from(transaction);
        return transactionEntityRepository.save(entity).toDomain();
    }

    @Override
    public List<Transaction> findAllByCategory(Category category) {
        return transactionEntityRepository.findAllByCategory(category)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAllByCategoryAndCreatedAtBetween(
            Category category,
            Instant start,
            Instant end
    ) {
        return transactionEntityRepository
                .findAllByCategoryAndCreatedAtBetween(category, start, end)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findTop10ByOrderByCreatedAtDesc() {
        return transactionEntityRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return transactionEntityRepository.count();
    }

    @Override
    public long sumAmountByCategoryAndDateBetween(
            Category category,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return transactionEntityRepository.sumAmountByCategoryAndDateBetween(
                category,
                startDate,
                endDate
        );
    }

}