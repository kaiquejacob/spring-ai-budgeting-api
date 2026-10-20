package dio.budgeting.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class Transaction {

    private final TransactionId id;
    private final String description;
    private final long amount;
    private final Category category;
    private final TransactionSource source;
    private final Instant createdAt;
    private final UUID userId;

    public Transaction(
            String description,
            long amount,
            Category category,
            TransactionSource source, UUID userId
    ) {
        this(new TransactionId(), description, amount, category, source, Instant.now(), userId);
    }

    public Transaction(
            TransactionId id,
            String description,
            long amount,
            Category category,
            TransactionSource source,
            Instant createdAt, UUID userId
    ) {
        this.userId = userId;
        validate(description, amount, category);

        this.id = id;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.source = source;
        this.createdAt = createdAt;
    }

    private void validate(String description, long amount, Category category) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("Descrição é obrigatória");
        }

        if (description.length() > 255) {
            throw new InvalidTransactionException("Descrição deve ter no máximo 255 caracteres");
        }

        if (amount <= 0) {
            throw new InvalidTransactionException("Valor deve ser maior que zero");
        }

        if (category == null) {
            throw new InvalidTransactionException("Categoria é obrigatória");
        }
    }
}