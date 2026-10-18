package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListRecentTransactionsUseCase {

    private final TransactionRepository transactionRepository;

    public ListRecentTransactionsUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionOutput> execute() {
        return transactionRepository
                .findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(TransactionOutput::from)
                .toList();
    }
}