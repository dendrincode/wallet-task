package com.wallet.exception;

public class WalletNotFoundException extends WalletException {
    public WalletNotFoundException(String userId) {
        super("Wallet not found for user: " + userId, "WALLET_NOT_FOUND");
    }
}
