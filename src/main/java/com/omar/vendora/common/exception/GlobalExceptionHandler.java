package com.omar.vendora.common.exception;

import com.omar.vendora.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = UUID.randomUUID().toString();
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ErrorResponse.ErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_ERROR",
            "Validation failed",
            details,
            correlationId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.of(
            "INVALID_CREDENTIALS",
            ex.getMessage(),
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.of(
            "INVALID_REFRESH_TOKEN",
            ex.getMessage(),
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(RateLimitExceededException ex) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.of(
            "RATE_LIMIT_EXCEEDED",
            ex.getMessage(),
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.of(
            "EMAIL_ALREADY_EXISTS",
            ex.getMessage(),
            List.of(new ErrorResponse.ErrorDetail("email", "Email is already registered")),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Database constraint violation [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            "DATA_INTEGRITY_VIOLATION",
            "A database constraint was violated",
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(OwnershipViolationException.class)
    public ResponseEntity<ErrorResponse> handleOwnershipViolationException(OwnershipViolationException ex) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.of(
            "FORBIDDEN",
            ex.getMessage() != null ? ex.getMessage() : "Access denied: you do not own this resource",
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correlationId = UUID.randomUUID().toString();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            ErrorResponse response = ErrorResponse.of(
                "UNAUTHORIZED",
                "Authentication is required to access this resource",
                List.of(),
                correlationId
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        ErrorResponse response = ErrorResponse.of(
            "FORBIDDEN",
            "Access denied: insufficient permissions",
            List.of(),
            correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception occurred [correlationId={}]", correlationId, ex);

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please try again later.",
            List.of(),
            correlationId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
