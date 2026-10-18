package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;

@Service
public class SumTransactionsByCategoryUseCase {

    private final TransactionRepository transactionRepository;

    public SumTransactionsByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public long execute(
            Category category,
            TransactionAiResponse.AiPeriod period
    ) {
        var transactions = switch (period) {
            case CURRENT_MONTH -> {
                var now = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
                var start = now.atDay(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
                var end = now.plusMonths(1).atDay(1)
                        .atStartOfDay(ZoneId.of("America/Sao_Paulo"))
                        .toInstant();

                yield transactionRepository.findAllByCategoryAndCreatedAtBetween(
                        category,
                        start,
                        end
                );
            }
            case ALL_TIME -> transactionRepository.findAllByCategory(category);
        };

        return transactions.stream()
                .mapToLong(Transaction::getAmount)
                .sum();
    }
}