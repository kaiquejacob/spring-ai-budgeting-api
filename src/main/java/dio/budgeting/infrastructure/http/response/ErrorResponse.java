package dio.budgeting.infrastructure.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.Clock;

@Schema(description = "Formato padrão de erro da API")
public record ErrorResponse(
        @Schema(description = "Código HTTP", example = "400")
        int status,
        @Schema(description = "Código interno do erro", example = "INVALID_REQUEST")
        String error,
        @Schema(description = "Mensagem clara sobre a correção necessária", example = "Campos inválidos: amount: O valor deve ser maior que zero.")
        String message,
        @Schema(description = "Data e hora do erro", example = "2026-11-23T23:19:01Z")
        Instant timestamp
) {

    public static ErrorResponse of(int status, String error, String message, Clock clock) {
        return new ErrorResponse(status, error, message, clock.instant());
    }
}