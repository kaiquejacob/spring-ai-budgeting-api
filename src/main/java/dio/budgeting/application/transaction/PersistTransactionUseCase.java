package dio.budgeting.application.transaction;

import dio.budgeting.application.transaction.PersistTransactionInput;
import dio.budgeting.application.transaction.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;

@Service
public class PersistTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public PersistTransactionUseCase(
            TransactionRepository transactionRepository,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        var transaction = transactionRepository.save(
                new Transaction(
                        input.description(),
                        input.amountInCents(),
                        input.category(),
                        input.source(),
                        input.userId(),
                        clock
                )
        );

        return TransactionOutput.from(transaction);
    }
}