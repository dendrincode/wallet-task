package com.wallet.exception;

import com.wallet.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle WalletException - Base exception for wallet service
     */
    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ErrorResponse> handleWalletException(
            WalletException ex,
            WebRequest request) {
        log.error("Wallet exception occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Wallet Error",
            ex.getMessage(),
            ex.getErrorCode(),
            HttpStatus.BAD_REQUEST.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle InsufficientBalanceException
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalanceException(
            InsufficientBalanceException ex,
            WebRequest request) {
        log.error("Insufficient balance: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Insufficient Balance",
            ex.getMessage(),
            "INSUFFICIENT_BALANCE",
            HttpStatus.PAYMENT_REQUIRED.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.PAYMENT_REQUIRED);
    }

    /**
     * Handle InvalidAmountException
     */
    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmountException(
            InvalidAmountException ex,
            WebRequest request) {
        log.error("Invalid amount: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Invalid Amount",
            ex.getMessage(),
            "INVALID_AMOUNT",
            HttpStatus.BAD_REQUEST.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle WalletNotActiveException
     */
    @ExceptionHandler(WalletNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotActiveException(
            WalletNotActiveException ex,
            WebRequest request) {
        log.error("Wallet not active: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            "Wallet Not Active",
            ex.getMessage(),
            "WALLET_NOT_ACTIVE",
            HttpStatus.CONFLICT.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setTimestamp(LocalDateTime.now());

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle WalletNotFoundException
     */
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFoundException(
            WalletNotFoundException ex,
            WebRequest request) {
        log.error("Wallet not found: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Not Found",
            ex.getMessage(),
            "WALLET_NOT_FOUND",
            HttpStatus.NOT_FOUND.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle MethodArgumentNotValidException for request validation errors
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.error("Validation error: {}", ex.getMessage());
        
        StringBuilder details = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            details.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; ")
        );
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Validation Failed",
            "Request validation failed",
            "VALIDATION_ERROR",
            HttpStatus.BAD_REQUEST.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setDetails(details.toString());
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "Internal Server Error",
            "An unexpected error occurred",
            "INTERNAL_SERVER_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setDetails(ex.getMessage());
        errorResponse.setTimestamp(LocalDateTime.now());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
