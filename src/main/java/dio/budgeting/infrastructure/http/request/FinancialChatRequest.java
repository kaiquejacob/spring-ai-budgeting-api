package dio.budgeting.infrastructure.http.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinancialChatRequest(
        @NotBlank(message = "Informe uma mensagem não vazia.")
        @Size(max = 2000, message = "A mensagem deve ter no máximo 2000 caracteres.")
        String message
) {
}