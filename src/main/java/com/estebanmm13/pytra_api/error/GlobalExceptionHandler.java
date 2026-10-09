package com.estebanmm13.pytra_api.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResource(DuplicateResourceException e) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(InvalidCredentialException.class)
    public ResponseEntity<ApiError> handleInvalidCredential(InvalidCredentialException e) {
        ApiError apiError = new ApiError(HttpStatus.UNAUTHORIZED.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiError);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerified(EmailNotVerifiedException e) {
        ApiError apiError = new ApiError(HttpStatus.FORBIDDEN.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException e) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ApiError> handleExpiredToken(ExpiredTokenException e) {
        ApiError apiError = new ApiError(HttpStatus.GONE.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.GONE).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
       List<FieldError> errors = e.getBindingResult().getFieldErrors();
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : errors) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST.value(), "Validation failed", fieldErrors, LocalDateTime.now());
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException e) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // The raw message contains SQL and constraint details; never echo it to clients.
        ApiError apiError = new ApiError(HttpStatus.CONFLICT.value(), "Conflict with existing data", Map.of(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }


}
