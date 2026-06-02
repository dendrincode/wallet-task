package com.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private String walletId;      // internal wallet PK (UUID, auto-generated)
    private String userId;        // UUID v4 format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
    private BigDecimal balance;
    private String currency;        // Will be serialized from enum (USD, EUR, GBP)
    private String status;          // Will be serialized from enum (ACTIVE, FROZEN, CLOSED)
    private BigDecimal totalDeposited;
    private BigDecimal totalTraded;
    private String createdAt;
    private String updatedAt;
    private String lastTransactionAt;
    private String description;
    private List<TransactionHistory> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionHistory {
        private Long transactionId;
        private String type;
        private BigDecimal amount;
        private String timestamp;
        private BigDecimal balanceAfter;
    }
}
