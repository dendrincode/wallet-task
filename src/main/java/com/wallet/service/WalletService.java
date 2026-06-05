package com.wallet.service;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.OperationResponse;
import com.wallet.dto.TradeRequest;
import com.wallet.dto.TransactionPageResponse;
import com.wallet.dto.UpdateWalletRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.entity.CurrencyType;
import com.wallet.entity.WalletStatus;
import com.wallet.exception.WalletNotActiveException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final R2dbcEntityTemplate r2dbcTemplate;

    public Mono<OperationResponse> deposit(String userId, DepositRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.just(new OperationResponse("error", null, "Amount must be greater than zero"));
        }
        return transactionRepository.findByIdempotencyKeyAndUserId(request.getIdempotencyKey(), userId)
                .map(existing -> new OperationResponse("success", existing.getBalanceAfter(), "Deposit already processed"))
                .switchIfEmpty(Mono.defer(() -> getOrCreateWallet(userId, request.getDescription())
                        .flatMap(wallet -> {
                            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                                return Mono.error(new WalletNotActiveException(wallet.getStatus().name()));
                            }
                            LocalDateTime now = LocalDateTime.now();
                            wallet.setBalance(wallet.getBalance().add(request.getAmount()));
                            wallet.setTotalDeposited(wallet.getTotalDeposited().add(request.getAmount()));
                            wallet.setLastTransactionAt(now);
                            wallet.setUpdatedAt(now);
                            return walletRepository.save(wallet)
                                    .flatMap(saved -> {
                                        Transaction tx = new Transaction(
                                                saved.getWalletId(),
                                                Transaction.TransactionType.DEPOSIT,
                                                request.getAmount(),
                                                now,
                                                request.getIdempotencyKey(),
                                                saved.getBalance()
                                        );
                                        tx.setId(UUID.randomUUID().toString());
                                        return r2dbcTemplate.insert(tx)
                                                .thenReturn(new OperationResponse("success", saved.getBalance(), "Deposit completed"));
                                    });
                        })
                ));
    }

    public Mono<OperationResponse> trade(String userId, TradeRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.just(new OperationResponse("error", null, "Amount must be greater than zero"));
        }
        return transactionRepository.findByIdempotencyKeyAndUserId(request.getIdempotencyKey(), userId)
                .map(existing -> new OperationResponse("success", existing.getBalanceAfter(), "Trade already processed"))
                .switchIfEmpty(Mono.defer(() -> walletRepository.findByUserIdForUpdate(userId)
                        .switchIfEmpty(Mono.defer(() -> createWallet(userId, null)))
                        .flatMap(wallet -> {
                            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                                return Mono.error(new WalletNotActiveException(wallet.getStatus().name()));
                            }
                            BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
                            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                                return Mono.just(new OperationResponse("error", wallet.getBalance(),
                                        "Insufficient balance for trade. Required: " + request.getAmount() +
                                        ", Available: " + wallet.getBalance()));
                            }
                            LocalDateTime now = LocalDateTime.now();
                            wallet.setBalance(newBalance);
                            wallet.setTotalTraded(wallet.getTotalTraded().add(request.getAmount()));
                            wallet.setLastTransactionAt(now);
                            wallet.setUpdatedAt(now);
                            return walletRepository.save(wallet)
                                    .flatMap(saved -> {
                                        Transaction tx = new Transaction(
                                                saved.getWalletId(),
                                                Transaction.TransactionType.TRADE,
                                                request.getAmount(),
                                                now,
                                                request.getIdempotencyKey(),
                                                saved.getBalance()
                                        );
                                        tx.setId(UUID.randomUUID().toString());
                                        return r2dbcTemplate.insert(tx)
                                                .thenReturn(new OperationResponse("success", saved.getBalance(), "Trade completed"));
                                    });
                        })
                ));
    }

    @Transactional(readOnly = true)
    public Mono<WalletResponse> getWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .flatMap(wallet -> transactionRepository.findByUserIdPaged(userId, 10, 0)
                        .collectList()
                        .map(txs -> mapToWalletResponse(wallet, txs)))
                .switchIfEmpty(Mono.just(new WalletResponse(
                        null, userId, BigDecimal.ZERO, CurrencyType.EUR.toString(),
                        WalletStatus.ACTIVE.toString(), BigDecimal.ZERO, BigDecimal.ZERO,
                        null, null, null, null, List.of()
                )));
    }

    public Mono<WalletResponse> updateWallet(String userId, UpdateWalletRequest request) {
        return getOrCreateWallet(userId, null)
                .flatMap(wallet -> {
                    if (request.getDescription() != null) {
                        wallet.setDescription(request.getDescription().trim().isEmpty()
                                ? null : request.getDescription().trim());
                        wallet.setUpdatedAt(LocalDateTime.now());
                        return walletRepository.save(wallet);
                    }
                    return Mono.just(wallet);
                })
                .flatMap(wallet -> transactionRepository.findByUserIdPaged(userId, 10, 0)
                        .collectList()
                        .map(txs -> mapToWalletResponse(wallet, txs)));
    }

    @Transactional(readOnly = true)
    public Mono<TransactionPageResponse> getTransactions(String userId, int page, int size) {
        long offset = (long) page * size;
        return walletRepository.findByUserId(userId)
                .flatMap(wallet -> Mono.zip(
                        transactionRepository.findByUserIdPaged(userId, size, offset).collectList(),
                        transactionRepository.countByUserId(userId)
                ).map(tuple -> {
                    List<Transaction> txs = tuple.getT1();
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    List<TransactionPageResponse.TransactionItem> items = txs.stream()
                            .map(tx -> new TransactionPageResponse.TransactionItem(
                                    tx.getId(),
                                    tx.getType().toString(),
                                    tx.getAmount(),
                                    tx.getTimestamp().toString(),
                                    tx.getBalanceAfter()
                            ))
                            .toList();
                    return new TransactionPageResponse(userId, items, page, size, total, totalPages, (page + 1) < totalPages);
                }))
                .switchIfEmpty(Mono.just(new TransactionPageResponse(userId, List.of(), page, size, 0, 0, false)));
    }

    public Mono<WalletResponse> freezeWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new WalletNotFoundException("Wallet not found for user: " + userId)))
                .flatMap(wallet -> {
                    if (wallet.getStatus() == WalletStatus.CLOSED) {
                        return Mono.error(new WalletNotActiveException("CLOSED"));
                    }
                    if (wallet.getStatus() == WalletStatus.FROZEN) {
                        return Mono.error(new WalletNotActiveException("FROZEN"));
                    }
                    wallet.setStatus(WalletStatus.FROZEN);
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(wallet);
                })
                .flatMap(wallet -> transactionRepository.findByUserIdPaged(userId, 10, 0)
                        .collectList()
                        .map(txs -> mapToWalletResponse(wallet, txs)));
    }

    public Mono<WalletResponse> closeWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new WalletNotFoundException("Wallet not found for user: " + userId)))
                .flatMap(wallet -> {
                    if (wallet.getStatus() == WalletStatus.CLOSED) {
                        return Mono.error(new WalletNotActiveException("CLOSED"));
                    }
                    wallet.setStatus(WalletStatus.CLOSED);
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(wallet);
                })
                .flatMap(wallet -> transactionRepository.findByUserIdPaged(userId, 10, 0)
                        .collectList()
                        .map(txs -> mapToWalletResponse(wallet, txs)));
    }

    private Mono<Wallet> getOrCreateWallet(String userId, String description) {
        return walletRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> createWallet(userId, description)));
    }

    private Mono<Wallet> createWallet(String userId, String description) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> r2dbcTemplate.insert(new User(userId))))
                .flatMap(user -> {
                    Wallet newWallet = new Wallet(userId);
                    newWallet.setWalletId(UUID.randomUUID().toString());
                    LocalDateTime now = LocalDateTime.now();
                    newWallet.setCreatedAt(now);
                    newWallet.setUpdatedAt(now);
                    if (description != null && !description.trim().isEmpty()) {
                        newWallet.setDescription(description.trim());
                    }
                    return r2dbcTemplate.insert(newWallet);
                });
    }

    private WalletResponse mapToWalletResponse(Wallet wallet, List<Transaction> txs) {
        List<WalletResponse.TransactionHistory> history = txs.stream()
                .map(tx -> new WalletResponse.TransactionHistory(
                        tx.getId(),
                        tx.getType().toString(),
                        tx.getAmount(),
                        tx.getTimestamp().toString(),
                        tx.getBalanceAfter()
                ))
                .toList();

        return new WalletResponse(
                wallet.getWalletId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCurrency().toString(),
                wallet.getStatus().toString(),
                wallet.getTotalDeposited(),
                wallet.getTotalTraded(),
                wallet.getCreatedAt() != null ? wallet.getCreatedAt().toString() : null,
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString() : null,
                wallet.getLastTransactionAt() != null ? wallet.getLastTransactionAt().toString() : null,
                wallet.getDescription(),
                history
        );
    }
}
