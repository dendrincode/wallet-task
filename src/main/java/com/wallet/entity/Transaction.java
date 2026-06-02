package com.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_wallet_timestamp", columnList = "wallet_id, timestamp"),
    @Index(name = "idx_transaction_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(unique = true, nullable = true, length = 36)
    private String idempotencyKey;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    public enum TransactionType {
        DEPOSIT, TRADE
    }

    public Transaction(Wallet wallet, TransactionType type, BigDecimal amount, 
                       LocalDateTime timestamp, String idempotencyKey, BigDecimal balanceAfter) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.idempotencyKey = idempotencyKey;
        this.balanceAfter = balanceAfter;
    }
}
