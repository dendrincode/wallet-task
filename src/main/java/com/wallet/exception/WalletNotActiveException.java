package com.wallet.exception;

public class WalletNotActiveException extends WalletException {
    public WalletNotActiveException(String status) {
        super("Wallet is " + status + " and cannot accept transactions", "WALLET_NOT_ACTIVE");
    }
}
