package com.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPageResponse {
    private String userId;
    private List<TransactionItem> transactions;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionItem {
        private String transactionId;
        private String type;
        private BigDecimal amount;
        private String timestamp;
        private BigDecimal balanceAfter;
    }
}
