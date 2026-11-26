package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.Clock;
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
    private final Clock clock;

    @Autowired
    public PendingTransactionService(Clock clock) {
        this.clock = clock;
    }

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
                        clock.instant().plusSeconds(EXPIRATION_SECONDS)
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

        if (!clock.instant().isBefore(pending.expiresAt())) {
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