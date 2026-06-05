package com.wallet.repository;

import com.wallet.entity.Wallet;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface WalletRepository extends ReactiveCrudRepository<Wallet, String> {

    @Query("SELECT * FROM wallets WHERE user_id = :userId")
    Mono<Wallet> findByUserId(@Param("userId") String userId);

    // Pessimistic write lock — replaces @Lock(PESSIMISTIC_WRITE). Must run inside @Transactional.
    @Query("SELECT * FROM wallets WHERE user_id = :userId FOR UPDATE")
    Mono<Wallet> findByUserIdForUpdate(@Param("userId") String userId);
}
