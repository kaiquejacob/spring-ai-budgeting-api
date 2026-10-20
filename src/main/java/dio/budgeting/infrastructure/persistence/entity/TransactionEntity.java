package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {

    @Id
    private UUID id;

    private String description;
    private long amount;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private TransactionSource source;

    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public static TransactionEntity from(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId().uuid(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getSource(),
                transaction.getCreatedAt(),
                transaction.getUserId()
        );
    }

    public Transaction toDomain() {
        return new Transaction(
                new TransactionId(this.id),
                this.description,
                this.amount,
                this.category,
                this.source,
                this.createdAt,
                this.userId
        );
    }


}