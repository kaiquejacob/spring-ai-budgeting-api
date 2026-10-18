package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingTransactionService {

    private static final int EXPIRATION_SECONDS = 300;

    private record PendingTransaction(
            TransactionAiResponse proposal,
            Instant expiresAt
    ) {
    }

    private final Map<UUID, PendingTransaction> pendingTransactions =
            new ConcurrentHashMap<>();

    public UUID save(TransactionAiResponse proposal) {
        var token = UUID.randomUUID();

        pendingTransactions.put(
                token,
                new PendingTransaction(
                        proposal,
                        Instant.now().plusSeconds(EXPIRATION_SECONDS)
                )
        );

        return token;
    }

    public TransactionAiResponse consume(UUID token) {
        var pending = pendingTransactions.remove(token);

        if (pending == null) {
            throw new PendingTransactionException("Proposta inválida ou já confirmada");
        }

        if (Instant.now().isAfter(pending.expiresAt())) {
            throw new PendingTransactionException("Proposta expirada");
        }

        return pending.proposal();
    }
}