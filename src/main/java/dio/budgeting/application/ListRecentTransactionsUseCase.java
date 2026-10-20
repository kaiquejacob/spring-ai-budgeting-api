package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListRecentTransactionsUseCase {

    private final TransactionRepository transactionRepository;

    public ListRecentTransactionsUseCase(
            TransactionRepository transactionRepository
    ) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionOutput> execute(UUID userId) {
        return transactionRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TransactionOutput::from)
                .toList();
    }
}