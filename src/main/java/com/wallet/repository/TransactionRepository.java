package com.wallet.repository;

import com.wallet.entity.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TransactionRepository extends ReactiveCrudRepository<Transaction, String> {

    @Query("SELECT t.* FROM transactions t JOIN wallets w ON t.wallet_id = w.wallet_id " +
           "WHERE t.idempotency_key = :key AND w.user_id = :userId")
    Mono<Transaction> findByIdempotencyKeyAndUserId(
            @Param("key") String idempotencyKey,
            @Param("userId") String userId);

    @Query("SELECT t.* FROM transactions t JOIN wallets w ON t.wallet_id = w.wallet_id " +
           "WHERE w.user_id = :userId ORDER BY t.timestamp DESC, t.id DESC " +
           "LIMIT :size OFFSET :offset")
    Flux<Transaction> findByUserIdPaged(
            @Param("userId") String userId,
            @Param("size") int size,
            @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM transactions t JOIN wallets w ON t.wallet_id = w.wallet_id " +
           "WHERE w.user_id = :userId")
    Mono<Long> countByUserId(@Param("userId") String userId);
}
