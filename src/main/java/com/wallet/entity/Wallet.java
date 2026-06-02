package com.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_wallet_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_wallet_status", columnList = "status"),
    @Index(name = "idx_wallet_created_at", columnList = "created_at"),
    @Index(name = "idx_wallet_currency", columnList = "currency")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wallet_id", length = 36, updatable = false, nullable = false)
    private String walletId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private CurrencyType currency = CurrencyType.EUR;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTraded = BigDecimal.ZERO;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column(length = 255)
    private String description;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions;

    public Wallet(User user) {
        this.user = user;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
        this.currency = CurrencyType.EUR;
        this.totalDeposited = BigDecimal.ZERO;
        this.totalTraded = BigDecimal.ZERO;
        // walletId is assigned by Hibernate on persist
    }
}
