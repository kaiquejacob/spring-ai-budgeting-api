package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingTransactionService {

    private static final int EXPIRATION_SECONDS = 300;

    private record PendingTransaction(
            UUID userId,
            TransactionAiResponse proposal,
            Instant expiresAt
    ) {
    }

    private final Map<UUID, PendingTransaction> pendingTransactions =
            new ConcurrentHashMap<>();

    public UUID save(
            UUID userId,
            TransactionAiResponse proposal
    ) {
        Objects.requireNonNull(userId, "O usuário é obrigatório.");
        Objects.requireNonNull(proposal, "A proposta é obrigatória.");

        var token = UUID.randomUUID();

        pendingTransactions.put(
                token,
                new PendingTransaction(
                        userId,
                        proposal,
                        Instant.now().plusSeconds(EXPIRATION_SECONDS)
                )
        );

        return token;
    }

    public TransactionAiResponse consume(UUID token, UUID userId) {
        Objects.requireNonNull(token, "O token é obrigatório.");
        Objects.requireNonNull(userId, "O usuário é obrigatório.");

        var pending = pendingTransactions.get(token);

        if (pending == null || !pending.userId().equals(userId)) {
            throw new PendingTransactionException(
                    "Proposta não encontrada ou indisponível para este usuário."
            );
        }

        if (!Instant.now().isBefore(pending.expiresAt())) {
            pendingTransactions.remove(token, pending);

            throw new PendingTransactionException("Proposta expirada.");
        }

        if (!pendingTransactions.remove(token, pending)) {
            throw new PendingTransactionException(
                    "Proposta inválida ou já confirmada."
            );
        }

        return pending.proposal();
    }
}