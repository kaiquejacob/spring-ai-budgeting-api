package dio.budgeting.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    List<Transaction> findByUserIdAndCategory(
            UUID userId,
            Category category
    );

    long sumAmountByUserIdCategoryAndCreatedAtBetween(
            UUID userId,
            Category category,
            Instant startDate,
            Instant endDate
    );

    List<Transaction> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}