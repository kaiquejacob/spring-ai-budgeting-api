package dio.budgeting.domain;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    List<Transaction> findAllByCategory(Category category);

    List<Transaction> findAllByCategoryAndCreatedAtBetween(
            Category category,
            Instant start,
            Instant end
    );

    List<Transaction> findTop10ByOrderByCreatedAtDesc();

    long count();
}