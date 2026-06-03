package com.wallet.repository;

import com.wallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKeyAndWalletUserUserId(String idempotencyKey, String userId);

    @Query("SELECT t FROM Transaction t WHERE t.wallet.user.userId = :userId ORDER BY t.timestamp DESC, t.id DESC")
    Page<Transaction> findByWalletUserIdOrderByTimestampDescIdDesc(@Param("userId") String userId, Pageable pageable);
}
