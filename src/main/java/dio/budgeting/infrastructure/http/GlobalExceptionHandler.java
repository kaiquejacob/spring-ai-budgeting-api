package dio.budgeting.infrastructure.http;

import dio.budgeting.application.proposal.PendingTransactionException;
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
import dio.budgeting.application.conversation.SessionAccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

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
                        "timestamp", clock.instant().toString()
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
                        "timestamp", clock.instant().toString()
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

        String fieldName;
        String correction;

        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            fieldName = mismatch.getName();
            correction = "Informe um valor válido.";
        } else if (exception instanceof MissingRequestHeaderException missing) {
            fieldName = missing.getHeaderName();
            correction = "Informe este header.";
        } else {
            fieldName = "requisição";
            correction = "A requisição contém parâmetros inválidos.";
        }

        return badRequest(
                "INVALID_REQUEST",
                "Campos inválidos: " + fieldName + ": " + correction
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Set<String> fieldErrors = new LinkedHashSet<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.add(error.getField() + ": " + error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                validationBadRequestBody(fieldErrors)
        );
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Set<String> fieldErrors = new LinkedHashSet<>();

        exception.getParameterValidationResults().forEach(result -> {
            String fieldName = result.getMethodParameter().getParameterName();

            if (fieldName == null) {
                fieldName = "parâmetro";
            }

            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                fieldErrors.add(fieldName + ": " + error.getDefaultMessage());
            }
        });

        if (fieldErrors.isEmpty()) {
            for (MessageSourceResolvable error :
                    exception.getCrossParameterValidationResults()) {
                fieldErrors.add("requisição: " + error.getDefaultMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                validationBadRequestBody(fieldErrors)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Set<String> fieldErrors = new LinkedHashSet<>();

        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.add(
                        violation.getPropertyPath().toString()
                                + ": "
                                + violation.getMessage()
                )
        );

        return validationBadRequest(fieldErrors);
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
                        "timestamp", clock.instant().toString()
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
                        "timestamp", clock.instant().toString()
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
                        "timestamp", clock.instant().toString()
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
                        "timestamp", clock.instant().toString()
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
                        "timestamp", clock.instant().toString()
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
                "timestamp", clock.instant().toString()
        );
    }

    private ResponseEntity<Map<String, Object>> validationBadRequest(
            Set<String> fieldErrors
    ) {
        String message = fieldErrors.isEmpty()
                ? "Campos inválidos: requisição: Corrija os dados informados."
                : "Campos inválidos: " + String.join("; ", fieldErrors);

        return badRequest("INVALID_REQUEST", message);
    }

    private Map<String, Object> validationBadRequestBody(
            Set<String> fieldErrors
    ) {
        String message = fieldErrors.isEmpty()
                ? "Campos inválidos: requisição: Corrija os dados informados."
                : "Campos inválidos: " + String.join("; ", fieldErrors);

        return badRequestBody("INVALID_REQUEST", message);
    }

}
