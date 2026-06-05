package com.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @Column("wallet_id")
    private String walletId;

    @Column("user_id")
    private String userId;

    @Column("balance")
    private BigDecimal balance = BigDecimal.ZERO;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("status")
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column("currency")
    private CurrencyType currency = CurrencyType.EUR;

    @Column("total_deposited")
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    @Column("total_traded")
    private BigDecimal totalTraded = BigDecimal.ZERO;

    @Column("last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column("description")
    private String description;

    public Wallet(String userId) {
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
        this.currency = CurrencyType.EUR;
        this.totalDeposited = BigDecimal.ZERO;
        this.totalTraded = BigDecimal.ZERO;
    }
}
