package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransactionRequest(
        @NotBlank(message = "Informe uma descrição não vazia.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String description,
        @NotNull(message = "Informe uma categoria.")
        Category category,
        @Positive(message = "O valor deve ser maior que zero.")
        long amount
) {

    public PersistTransactionInput toInput(UUID userId) {
        return new PersistTransactionInput(
                userId,
                description,
                amount * 100,
                category,
                TransactionSource.MANUAL
        );
    }
}