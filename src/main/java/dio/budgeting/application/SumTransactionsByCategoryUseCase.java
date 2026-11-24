package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionPeriod;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.UUID;

@Service
public class SumTransactionsByCategoryUseCase {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public SumTransactionsByCategoryUseCase(
            TransactionRepository transactionRepository,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public long execute(
            UUID userId,
            Category category,
            TransactionAiResponse.AiPeriod period
    ) {
        var transactionPeriod = TransactionPeriod.from(period, clock);

        return transactionRepository.sumAmountByUserIdCategoryAndCreatedAtBetween(
                userId,
                category,
                transactionPeriod.startDate(),
                transactionPeriod.endDate()
        );
    }
}