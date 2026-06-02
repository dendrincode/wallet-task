package com.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String error;
    private String message;
    private String errorCode;
    private String path;
    private LocalDateTime timestamp;
    private int status;
    private String details;

    public ErrorResponse(String error, String message, String errorCode, int status) {
        this.error = error;
        this.message = message;
        this.errorCode = errorCode;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String error, String message, int status) {
        this(error, message, null, status);
    }
}
