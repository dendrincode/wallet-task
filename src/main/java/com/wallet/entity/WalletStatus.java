package com.wallet.entity;

public enum WalletStatus {
    ACTIVE,      // Wallet is active and can transact
    FROZEN,      // Wallet is frozen, no transactions allowed
    CLOSED       // Wallet is closed permanently
}
