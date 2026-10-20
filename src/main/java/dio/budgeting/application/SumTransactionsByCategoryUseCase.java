package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionPeriod;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

@Service
public class SumTransactionsByCategoryUseCase {

    private final TransactionRepository transactionRepository;

    public SumTransactionsByCategoryUseCase(
            TransactionRepository transactionRepository
    ) {
        this.transactionRepository = transactionRepository;
    }

    public long execute(
            Category category,
            TransactionAiResponse.AiPeriod period
    ) {
        var transactionPeriod = TransactionPeriod.from(period);

        return transactionRepository.sumAmountByCategoryAndDateBetween(
                category,
                transactionPeriod.startDate(),
                transactionPeriod.endDate()
        );
    }
}