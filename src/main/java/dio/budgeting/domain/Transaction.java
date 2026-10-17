package dio.budgeting.domain;

import lombok.Getter;

@Getter
public class Transaction {

    private final TransactionId id;
    private final String description;
    private final long amount;
    private final Category category;
    private final TransactionSource source;

    public Transaction(
            String description,
            long amount,
            Category category,
            TransactionSource source
    ) {
        validate(description, amount, category);

        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.source = source;
    }

    public Transaction(
            TransactionId id,
            String description,
            long amount,
            Category category,
            TransactionSource source
    ) {
        validate(description, amount, category);

        this.id = id;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.source = source;
    }

    private void validate(String description, long amount, Category category) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição é obrigatória");
        }

        if (description.length() > 255) {
            throw new IllegalArgumentException("Descrição deve ter no máximo 255 caracteres");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }

        if (category == null) {
            throw new IllegalArgumentException("Categoria é obrigatória");
        }
    }
}