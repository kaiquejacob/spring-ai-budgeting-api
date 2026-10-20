package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionPeriod;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SumTransactionsByCategoryUseCase {

    private final TransactionRepository transactionRepository;

    public SumTransactionsByCategoryUseCase(
            TransactionRepository transactionRepository
    ) {
        this.transactionRepository = transactionRepository;
    }

    public long execute(
            UUID userId,
            Category category,
            TransactionAiResponse.AiPeriod period
    ) {
        var transactionPeriod = TransactionPeriod.from(period);

        return transactionRepository.sumAmountByUserIdCategoryAndCreatedAtBetween(
                userId,
                category,
                transactionPeriod.startDate(),
                transactionPeriod.endDate()
        );
    }
}