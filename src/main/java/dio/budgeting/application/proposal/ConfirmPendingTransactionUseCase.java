package dio.budgeting.application.proposal;

import dio.budgeting.application.transaction.PersistTransactionInput;
import dio.budgeting.application.transaction.TransactionOutput;
import dio.budgeting.domain.TransactionSource;
import org.springframework.stereotype.Service;

import java.util.UUID;
import dio.budgeting.application.transaction.PersistTransactionUseCase;

@Service
public class ConfirmPendingTransactionUseCase {

    private final PendingTransactionService pendingTransactionService;
    private final PersistTransactionUseCase persistTransactionUseCase;

    public ConfirmPendingTransactionUseCase(
            PendingTransactionService pendingTransactionService,
            PersistTransactionUseCase persistTransactionUseCase
    ) {
        this.pendingTransactionService = pendingTransactionService;
        this.persistTransactionUseCase = persistTransactionUseCase;
    }

    public TransactionOutput execute(UUID token, UUID userId) {
        var proposal = pendingTransactionService.consume(token, userId);

        return persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        userId,
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI,
                        proposal.effectiveType(),
                        proposal.occurredAt()
                )
        );
    }
}
