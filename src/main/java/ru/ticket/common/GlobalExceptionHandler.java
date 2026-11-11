package ru.ticket.common;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final boolean exposeErrorDetails;

  public GlobalExceptionHandler(
      @Value("${app.expose-error-details:false}") boolean exposeErrorDetails) {
    this.exposeErrorDetails = exposeErrorDetails;
  }

  /** 4xx — контролируемая бизнес-ошибка. Сообщение user-facing, отдаём как есть. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
    log.warn("API error: {} → {}", e.status(), e.getMessage());
    return ResponseEntity.status(e.status())
        .body(
            Map.of(
                "error",
                e.getMessage() == null ? "Error" : e.getMessage(),
                "status",
                e.status().value()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    log.warn("Validation error: {}", msg);
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<Map<String,Object>> handleConflict(org.springframework.dao.DataIntegrityViolationException error) {
    log.warn("Data conflict: {}", error.getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","This record is already in use or depends on another record.","status",409));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidInput(IllegalArgumentException error) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                error.getMessage() == null ? "Invalid input" : error.getMessage(),
                "status",
                400));
  }

  /**
   * Любая необработанная ошибка. Логируем со stacktrace + requestId. Клиенту — generic message +
   * requestId (для поддержки). e.getMessage() может содержать пути к файлам, SQL, имена классов —
   * наружу отдаём только если явно включено property.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    log.error("Unhandled exception in controller [requestId={}]", requestId, e);

    String clientMessage =
        exposeErrorDetails && e.getMessage() != null
            ? e.getMessage()
            : "Internal error (requestId: " + requestId + ")";

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "error", clientMessage,
                "requestId", requestId));
  }
}
