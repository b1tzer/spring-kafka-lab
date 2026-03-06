package xpro.wang.kafkalab.server.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xpro.wang.kafkalab.server.model.ApiResponse;

/** Global REST exception mapping. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Handles validation failures.
   *
   * @param ex validation exception
   * @return unified API response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request");
    log.warn("Validation failed: {}", message);
    return ApiResponse.fail(message);
  }

  /**
   * Handles bad request argument exceptions.
   *
   * @param ex illegal argument exception
   * @return unified API response
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Illegal argument: {}", ex.getMessage());
    return ApiResponse.fail(ex.getMessage());
  }

  /**
   * Handles domain/state precondition failures.
   *
   * @param ex illegal state exception
   * @return unified API response
   */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleIllegalState(IllegalStateException ex) {
    log.warn("Illegal state: {}", ex.getMessage());
    return ApiResponse.fail(ex.getMessage());
  }

  /**
   * Handles uncaught exceptions.
   *
   * @param ex runtime exception
   * @return unified API response
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleGeneric(Exception ex) {
    log.error("Unhandled server exception", ex);
    return ApiResponse.fail(ex.getMessage());
  }
}
