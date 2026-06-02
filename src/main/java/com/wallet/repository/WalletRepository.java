package com.wallet.repository;

import com.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    Optional<Wallet> findByUserUserId(String userId);

    /**
     * Acquires a pessimistic write lock for the duration of the transaction.
     * Used exclusively for trade operations to prevent concurrent overdrafts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") String userId);
}
