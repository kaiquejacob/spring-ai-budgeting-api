package dio.budgeting.infrastructure.http;

import dio.budgeting.application.PendingTransactionException;
import dio.budgeting.application.ai.AiProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import dio.budgeting.application.SessionAccessDeniedException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleAiProcessingException(
            AiProcessingException exception
    ) {
        log.warn("AI processing failed: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                Map.of(
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "error", "AI_SERVICE_UNAVAILABLE",
                        "message", "Não foi possível processar sua solicitação agora. Tente novamente em instantes.",
                        "timestamp", Instant.now().toString()
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException exception
    ) {
        log.warn("Invalid request: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "INVALID_REQUEST",
                        "message", exception.getMessage(),
                        "timestamp", Instant.now().toString()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedError(
            Exception exception
    ) {
        log.error("Unexpected error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of(
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "error", "INTERNAL_ERROR",
                        "message", "Ocorreu um erro inesperado.",
                        "timestamp", Instant.now().toString()
                )
        );
    }

    @ExceptionHandler(PendingTransactionException.class)
    public ResponseEntity<Map<String, Object>> handlePendingTransactionException(
            PendingTransactionException exception
    ) {
        log.warn("Pending transaction error: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                        "status", HttpStatus.NOT_FOUND.value(),
                        "error", "PENDING_TRANSACTION_NOT_FOUND",
                        "message", exception.getMessage(),
                        "timestamp", Instant.now().toString()
                )
        );
    }

    @ExceptionHandler(SessionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleSessionAccessDenied(
            SessionAccessDeniedException exception
    ) {
        log.warn("Tentativa de acesso a sessão de outro usuário.");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of(
                        "status", HttpStatus.FORBIDDEN.value(),
                        "error", "SESSION_ACCESS_DENIED",
                        "message", exception.getMessage(),
                        "timestamp", Instant.now().toString()
                )
        );
    }

}