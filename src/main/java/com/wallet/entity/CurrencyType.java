package com.wallet.entity;

public enum CurrencyType {
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound");

    private final String displayName;

    CurrencyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
