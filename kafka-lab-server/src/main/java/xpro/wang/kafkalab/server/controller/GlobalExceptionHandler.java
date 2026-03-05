package xpro.wang.kafkalab.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xpro.wang.kafkalab.server.model.ApiResponse;

/**
 * Global REST exception mapping.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation failures.
     *
     * @param ex validation exception
     * @return unified API response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return ApiResponse.fail(message);
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
        return ApiResponse.fail(ex.getMessage());
    }
}
