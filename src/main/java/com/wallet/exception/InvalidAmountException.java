package com.wallet.exception;

public class InvalidAmountException extends WalletException {
    public InvalidAmountException(String message) {
        super(message, "INVALID_AMOUNT");
    }
}
