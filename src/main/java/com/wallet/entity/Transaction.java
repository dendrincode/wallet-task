package com.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @Column("id")
    private String id;

    @Column("wallet_id")
    private String walletId;

    @Column("type")
    private TransactionType type;

    @Column("amount")
    private BigDecimal amount;

    @Column("timestamp")
    private LocalDateTime timestamp;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("balance_after")
    private BigDecimal balanceAfter;

    public enum TransactionType {
        DEPOSIT, TRADE
    }

    public Transaction(String walletId, TransactionType type, BigDecimal amount,
                       LocalDateTime timestamp, String idempotencyKey, BigDecimal balanceAfter) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.idempotencyKey = idempotencyKey;
        this.balanceAfter = balanceAfter;
    }
}
