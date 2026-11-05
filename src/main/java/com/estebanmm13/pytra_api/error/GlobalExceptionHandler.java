package com.estebanmm13.pytra_api.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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

    @ExceptionHandler(RegistrationClosedException.class)
    public ResponseEntity<ApiError> handleRegistrationClosed(RegistrationClosedException e) {
        ApiError apiError = new ApiError(HttpStatus.FORBIDDEN.value(), RegistrationClosedException.CODE, Map.of(), LocalDateTime.now());
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

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException e) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST.value(), e.getMessage(), e.getFieldErrors(), LocalDateTime.now());
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ApiError> handleReauthenticationRequired(ReauthenticationRequiredException e) {
        ApiError apiError = new ApiError(HttpStatus.FORBIDDEN.value(), e.getMessage(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException e) {
        ApiError apiError = new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(apiError);
    }

    @ExceptionHandler(ResourceLimitExceededException.class)
    public ResponseEntity<ApiError> handleResourceLimitExceeded(ResourceLimitExceededException e) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT.value(), e.getMessage(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException e) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND.value(),e.getMessage(), Map.of() ,LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(SteamIntegrationException.class)
    public ResponseEntity<ApiError> handleSteamIntegration(SteamIntegrationException e) {
        ApiError apiError = new ApiError(e.getStatus().value(), e.getCode(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(e.getStatus()).body(apiError);
    }

    @ExceptionHandler(PsnIntegrationException.class)
    public ResponseEntity<ApiError> handlePsnIntegration(PsnIntegrationException e) {
        ApiError apiError = new ApiError(e.getStatus().value(), e.getCode(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(e.getStatus()).body(apiError);
    }

    @ExceptionHandler(IgdbIntegrationException.class)
    public ResponseEntity<ApiError> handleIgdbIntegration(IgdbIntegrationException e) {
        ApiError apiError = new ApiError(e.getStatus().value(), e.getCode(), Map.of(), LocalDateTime.now());
        return ResponseEntity.status(e.getStatus()).body(apiError);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // The raw message contains SQL and constraint details; never echo it to clients.
        ApiError apiError = new ApiError(HttpStatus.CONFLICT.value(), "Conflict with existing data", Map.of(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // The parser message quotes the payload and Java types; keep it out of the response.
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "Missing parameter", Map.of(e.getParameterName(), "Required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid parameter", Map.of(e.getName(), "Invalid value"));
    }

    /**
     * Last resort. Spring's own request errors (404 no route, 405, 415, method validation...) keep their status
     * in the ApiError shape; anything else is an unexpected failure: logged here, answered as a bare 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        // Left to their usual resolvers: Spring Security (401/403), @ResponseStatus exceptions (their own status)
        // and client disconnects (nothing to answer, not worth an error log).
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException
                || e instanceof AsyncRequestNotUsableException
                || AnnotatedElementUtils.hasAnnotation(e.getClass(), ResponseStatus.class)) {
            throw e;
        }
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            // Keep the headers Spring attaches (Allow on 405, Accept on 415...).
            return ResponseEntity.status(status).headers(errorResponse.getHeaders()).body(new ApiError(status.value(),
                    known != null ? known.getReasonPhrase() : "Request failed", Map.of(), LocalDateTime.now()));
        }
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", Map.of());
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fieldErrors, LocalDateTime.now()));
    }
}
