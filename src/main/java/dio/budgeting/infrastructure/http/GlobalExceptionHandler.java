package dio.budgeting.infrastructure.http;

import dio.budgeting.application.PendingTransactionException;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.infrastructure.http.response.ErrorResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(
            InvalidTransactionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        422,
                        "Invalid transaction",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(PendingTransactionException.class)
    public ResponseEntity<ErrorResponse> handlePendingTransaction(
            PendingTransactionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "Invalid confirmation",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<ErrorResponse> handleAiException(
            NonTransientAiException exception
    ) {
        log.warn("AI provider error: {}", exception.getMessage());

        var message = exception.getMessage() == null ? "" : exception.getMessage();

        if (message.contains("429")) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ErrorResponse.of(
                            429,
                            "Rate limit exceeded",
                            "Limite de uso da Groq atingido. Tente novamente mais tarde."
                    ));
        }

        if (message.contains("401")) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse.of(
                            503,
                            "AI provider unavailable",
                            "Não foi possível autenticar no provedor de IA."
                    ));
        }

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(
                        502,
                        "AI provider error",
                        "O provedor de IA não conseguiu processar a solicitação."
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(
            MaxUploadSizeExceededException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                        413,
                        "File too large",
                        "O arquivo de áudio excede o tamanho permitido."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal server error",
                        "Ocorreu um erro inesperado."
                ));
    }
}