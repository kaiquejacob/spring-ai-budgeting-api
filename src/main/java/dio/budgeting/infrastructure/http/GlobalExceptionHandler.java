package dio.budgeting.infrastructure.http;

import dio.budgeting.application.PendingTransactionException;
import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.domain.InvalidTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import dio.budgeting.application.SessionAccessDeniedException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransaction(
            InvalidTransactionException exception
    ) {
        log.warn("Invalid transaction: {}", exception.getMessage());

        return badRequest("INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> handleRequestMappingBadRequest(
            Exception exception
    ) {
        log.warn("Invalid request: {}", exception.getMessage());

        return badRequest("INVALID_REQUEST", "A requisição contém parâmetros inválidos.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.warn("Malformed request body.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                badRequestBody("INVALID_REQUEST", "O corpo da requisição é inválido.")
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.warn("HTTP method not supported: {}", exception.getMethod());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                Map.of(
                        "status", HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "error", "METHOD_NOT_ALLOWED",
                        "message", "Método HTTP não suportado.",
                        "timestamp", Instant.now().toString()
                )
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.warn("Media type not supported: {}", exception.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                Map.of(
                        "status", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        "error", "UNSUPPORTED_MEDIA_TYPE",
                        "message", "Tipo de conteúdo não suportado.",
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

    private ResponseEntity<Map<String, Object>> badRequest(
            String error,
            String message
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                badRequestBody(error, message)
        );
    }

    private Map<String, Object> badRequestBody(
            String error,
            String message
    ) {
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", error,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }

}