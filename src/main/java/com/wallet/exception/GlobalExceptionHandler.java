package com.wallet.exception;

import com.wallet.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotActiveException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWalletNotActiveException(
            WalletNotActiveException ex, ServerWebExchange exchange) {
        log.error("Wallet not active: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Wallet Not Active", ex.getMessage(), "WALLET_NOT_ACTIVE",
                HttpStatus.CONFLICT.value(), null, exchange), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWalletNotFoundException(
            WalletNotFoundException ex, ServerWebExchange exchange) {
        log.error("Wallet not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Not Found", ex.getMessage(), "WALLET_NOT_FOUND",
                HttpStatus.NOT_FOUND.value(), null, exchange), HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInsufficientBalanceException(
            InsufficientBalanceException ex, ServerWebExchange exchange) {
        log.error("Insufficient balance: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Insufficient Balance", ex.getMessage(), "INSUFFICIENT_BALANCE",
                HttpStatus.PAYMENT_REQUIRED.value(), null, exchange), HttpStatus.PAYMENT_REQUIRED));
    }

    @ExceptionHandler(InvalidAmountException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidAmountException(
            InvalidAmountException ex, ServerWebExchange exchange) {
        log.error("Invalid amount: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Invalid Amount", ex.getMessage(), "INVALID_AMOUNT",
                HttpStatus.BAD_REQUEST.value(), null, exchange), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(WalletException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWalletException(
            WalletException ex, ServerWebExchange exchange) {
        log.error("Wallet exception: {}", ex.getMessage(), ex);
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Wallet Error", ex.getMessage(), ex.getErrorCode(),
                HttpStatus.BAD_REQUEST.value(), null, exchange), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        log.error("Validation error: {}", ex.getMessage());
        StringBuilder details = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; "));
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Validation Failed", "Request validation failed", "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST.value(), details.toString(), exchange), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(
            ConstraintViolationException ex, ServerWebExchange exchange) {
        log.error("Constraint violation: {}", ex.getMessage());
        StringBuilder details = new StringBuilder();
        ex.getConstraintViolations().forEach(v ->
                details.append(v.getPropertyPath()).append(": ").append(v.getMessage()).append("; "));
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Validation Failed", "Request validation failed", "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST.value(), details.toString(), exchange), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGlobalException(
            Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        return Mono.just(new ResponseEntity<>(errorResponse(
                "Internal Server Error", "An unexpected error occurred", "INTERNAL_SERVER_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), exchange),
                HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private ErrorResponse errorResponse(String error, String message, String errorCode,
                                        int status, String details, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        ErrorResponse r = new ErrorResponse(error, message, errorCode, status);
        r.setPath(path);
        r.setDetails(details);
        r.setTimestamp(LocalDateTime.now());
        return r;
    }
}
