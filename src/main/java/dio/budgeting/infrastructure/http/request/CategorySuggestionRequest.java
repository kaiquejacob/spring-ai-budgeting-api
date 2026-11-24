package dio.budgeting.infrastructure.http.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategorySuggestionRequest(
        @NotBlank(message = "Informe uma descrição não vazia.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String description
) {
}