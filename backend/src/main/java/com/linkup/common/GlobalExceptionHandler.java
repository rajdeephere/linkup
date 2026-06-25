package com.linkup.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception → HTTP mapping. Keeps controllers clean and guarantees a
 * consistent ApiError body for every failure mode.
 *
 * Security note: a bad username and a bad password both return the SAME 401 with the
 * same message. Revealing "no such user" vs "wrong password" would let an attacker
 * enumerate valid usernames.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.validation(
                HttpStatus.BAD_REQUEST.value(), "Validation failed",
                "One or more fields are invalid", fieldErrors));
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ApiError> handleUsernameTaken(UsernameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid username or password"));
    }
}
