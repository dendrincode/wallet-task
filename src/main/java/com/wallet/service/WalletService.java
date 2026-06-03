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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * Deposit funds into a wallet with idempotency support.
     * Retried requests with the same idempotency key will not be applied twice.
     */
    public OperationResponse deposit(String userId, DepositRequest request) {
        walletRepository.findByUserUserId(userId).ifPresent(wallet -> {
            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException(wallet.getStatus().name());
            }
        });

        Optional<Transaction> existingDeposit = transactionRepository.findByIdempotencyKeyAndWalletUserUserId(request.getIdempotencyKey(), userId);
        if (existingDeposit.isPresent()) {
            return new OperationResponse("success", existingDeposit.get().getBalanceAfter(),
                "Deposit already processed");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return new OperationResponse("error", null, "Amount must be greater than zero");
        }

        Wallet wallet = walletRepository.findByUserUserId(userId)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseGet(() -> userRepository.save(new User(userId)));
                Wallet newWallet = new Wallet(user);
                newWallet.setTransactions(new ArrayList<>());
                if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                    newWallet.setDescription(request.getDescription().trim());
                }
                return walletRepository.save(newWallet);
            });

        // Re-check status on the fetched wallet — reduces the TOCTOU window from the early check.
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(wallet.getStatus().name());
        }

        LocalDateTime now = LocalDateTime.now();
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        wallet.setTotalDeposited(wallet.getTotalDeposited().add(request.getAmount()));
        wallet.setLastTransactionAt(now);
        wallet = walletRepository.save(wallet);

        transactionRepository.save(new Transaction(
            wallet,
            Transaction.TransactionType.DEPOSIT,
            request.getAmount(),
            now,
            request.getIdempotencyKey(),
            wallet.getBalance()
        ));

        return new OperationResponse("success", wallet.getBalance(), "Deposit completed");
    }

    /**
     * Debit funds for a trade with idempotency support.
     * Uses pessimistic locking to prevent concurrent overdrafts.
     * Trade is rejected if it would make balance negative.
     * Retried requests with the same idempotency key will not be applied twice.
     */
    public OperationResponse trade(String userId, TradeRequest request) {
        walletRepository.findByUserUserId(userId).ifPresent(wallet -> {
            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException(wallet.getStatus().name());
            }
        });

        Optional<Transaction> existingTrade = transactionRepository.findByIdempotencyKeyAndWalletUserUserId(request.getIdempotencyKey(), userId);
        if (existingTrade.isPresent()) {
            return new OperationResponse("success", existingTrade.get().getBalanceAfter(),
                "Trade already processed");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return new OperationResponse("error", null, "Amount must be greater than zero");
        }

        // Pessimistic write lock — prevents concurrent overdrafts
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseGet(() -> userRepository.save(new User(userId)));
                Wallet newWallet = new Wallet(user);
                newWallet.setTransactions(new ArrayList<>());
                return walletRepository.save(newWallet);
            });

        // Re-check status under the lock — a concurrent freeze/close may have committed
        // between the early status check above and lock acquisition here.
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(wallet.getStatus().name());
        }

        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return new OperationResponse("error", wallet.getBalance(),
                "Insufficient balance for trade. Required: " + request.getAmount() +
                ", Available: " + wallet.getBalance());
        }

        LocalDateTime now = LocalDateTime.now();
        wallet.setBalance(newBalance);
        wallet.setTotalTraded(wallet.getTotalTraded().add(request.getAmount()));
        wallet.setLastTransactionAt(now);
        wallet = walletRepository.save(wallet);

        transactionRepository.save(new Transaction(
            wallet,
            Transaction.TransactionType.TRADE,
            request.getAmount(),
            now,
            request.getIdempotencyKey(),
            wallet.getBalance()
        ));

        return new OperationResponse("success", wallet.getBalance(), "Trade completed");
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String userId) {
        return walletRepository.findByUserUserId(userId)
            .map(this::mapToWalletResponse)
            .orElseGet(() -> new WalletResponse(
                null, userId, BigDecimal.ZERO, CurrencyType.EUR.toString(),
                WalletStatus.ACTIVE.toString(), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, List.of()
            ));
    }

    public WalletResponse updateWallet(String userId, UpdateWalletRequest request) {
        Wallet wallet = walletRepository.findByUserUserId(userId)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseGet(() -> userRepository.save(new User(userId)));
                Wallet newWallet = new Wallet(user);
                newWallet.setTransactions(new ArrayList<>());
                return walletRepository.save(newWallet);
            });

        if (request.getDescription() != null) {
            wallet.setDescription(request.getDescription().trim().isEmpty() ? null : request.getDescription().trim());
            wallet = walletRepository.save(wallet);
        }

        return mapToWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(String userId, int page, int size) {
        Page<Transaction> txPage = transactionRepository
                .findByWalletUserIdOrderByTimestampDescIdDesc(userId, PageRequest.of(page, size));

        List<TransactionPageResponse.TransactionItem> items = txPage.getContent().stream()
                .map(tx -> new TransactionPageResponse.TransactionItem(
                        tx.getId(),
                        tx.getType().toString(),
                        tx.getAmount(),
                        tx.getTimestamp().toString(),
                        tx.getBalanceAfter()
                ))
                .collect(Collectors.toList());

        return new TransactionPageResponse(
                userId,
                items,
                txPage.getNumber(),
                txPage.getSize(),
                txPage.getTotalElements(),
                txPage.getTotalPages(),
                txPage.hasNext()
        );
    }

    public WalletResponse freezeWallet(String userId) {
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletNotActiveException("CLOSED");
        }
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new WalletNotActiveException("FROZEN");
        }

        wallet.setStatus(WalletStatus.FROZEN);
        walletRepository.save(wallet);
        return mapToWalletResponse(wallet);
    }

    public WalletResponse closeWallet(String userId) {
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletNotActiveException("CLOSED");
        }

        wallet.setStatus(WalletStatus.CLOSED);
        walletRepository.save(wallet);
        return mapToWalletResponse(wallet);
    }

    private WalletResponse mapToWalletResponse(Wallet wallet) {
        String userId = wallet.getUser().getUserId();

        List<WalletResponse.TransactionHistory> history = transactionRepository
                .findByWalletUserIdOrderByTimestampDescIdDesc(userId, PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(tx -> new WalletResponse.TransactionHistory(
                    tx.getId(),
                    tx.getType().toString(),
                    tx.getAmount(),
                    tx.getTimestamp().toString(),
                    tx.getBalanceAfter()
                ))
                .collect(Collectors.toList());

        return new WalletResponse(
            wallet.getWalletId(),
            userId,
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
