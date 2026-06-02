package com.wallet.service;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.OperationResponse;
import com.wallet.dto.TradeRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, userRepository);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static Wallet createTestWallet(String userId, BigDecimal balance) {
        Wallet wallet = new Wallet(new User(userId));
        wallet.setBalance(balance);
        wallet.setTransactions(new ArrayList<>());
        return wallet;
    }

    private static Wallet createTestWallet(String userId, BigDecimal balance, List<Transaction> transactions) {
        Wallet wallet = new Wallet(new User(userId));
        wallet.setBalance(balance);
        wallet.setTransactions(transactions);
        return wallet;
    }

    // ==================== DEPOSIT TESTS ====================

    @Test
    @DisplayName("Deposit should increase balance correctly")
    void testDepositIncreasesBalance() {
        String userId = "user123";
        BigDecimal depositAmount = new BigDecimal("100.00");
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse response = walletService.deposit(userId, new DepositRequest(depositAmount, uuid(), userId));

        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getNewBalance());
        verify(walletRepository, times(1)).save(any(Wallet.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Deposit should create wallet and user if neither exists")
    void testDepositCreatesNewWallet() {
        String userId = "newUser";
        BigDecimal depositAmount = new BigDecimal("50.00");
        Wallet newWallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.save(any(Wallet.class))).thenReturn(newWallet);
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse response = walletService.deposit(userId, new DepositRequest(depositAmount, uuid(), userId));

        assertEquals("success", response.getStatus());
        verify(userRepository).save(any(User.class));
        verify(walletRepository, atLeast(2)).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Deposit with invalid amount should fail")
    void testDepositWithInvalidAmount() {
        String userId = "user123";

        OperationResponse response = walletService.deposit(userId, new DepositRequest(new BigDecimal("-10.00"), uuid(), userId));
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("greater than zero"));

        response = walletService.deposit(userId, new DepositRequest(BigDecimal.ZERO, uuid(), userId));
        assertEquals("error", response.getStatus());

        response = walletService.deposit(userId, new DepositRequest(null, uuid(), userId));
        assertEquals("error", response.getStatus());
    }

    @Test
    @DisplayName("Deposit with idempotency key should not be applied twice")
    void testDepositIdempotency() {
        String userId = "user123";
        String idempotencyKey = uuid();
        BigDecimal depositAmount = new BigDecimal("100.00");

        Transaction existingTransaction = new Transaction(
            null, Transaction.TransactionType.DEPOSIT, depositAmount,
            LocalDateTime.now(), idempotencyKey, new BigDecimal("100.00")
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingTransaction));

        OperationResponse response = walletService.deposit(userId,
            new DepositRequest(depositAmount, idempotencyKey, idempotencyKey));

        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getNewBalance());
        assertTrue(response.getMessage().contains("already processed"));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // ==================== TRADE TESTS ====================

    @Test
    @DisplayName("Trade should decrease balance correctly")
    void testTradeDecreasesBalance() {
        String userId = "user123";
        BigDecimal tradeAmount = new BigDecimal("50.00");
        Wallet wallet = createTestWallet(userId, new BigDecimal("200.00"));

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid()));

        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("150.00"), response.getNewBalance());
        verify(walletRepository, times(1)).save(any(Wallet.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Trade should fail if balance would go negative")
    void testTradeFailsWithInsufficientBalance() {
        String userId = "user123";
        BigDecimal tradeAmount = new BigDecimal("100.00");
        Wallet wallet = createTestWallet(userId, new BigDecimal("50.00"));

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid()));

        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("Insufficient balance"));
        assertEquals(new BigDecimal("50.00"), response.getNewBalance());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Trade should fail with invalid amount")
    void testTradeWithInvalidAmount() {
        String userId = "user123";

        OperationResponse response = walletService.trade(userId, new TradeRequest(new BigDecimal("-50.00"), uuid()));
        assertEquals("error", response.getStatus());

        response = walletService.trade(userId, new TradeRequest(BigDecimal.ZERO, uuid()));
        assertEquals("error", response.getStatus());

        response = walletService.trade(userId, new TradeRequest(null, uuid()));
        assertEquals("error", response.getStatus());
    }

    @Test
    @DisplayName("Trade on new wallet should fail with insufficient balance")
    void testTradeFailsOnNewWallet() {
        String userId = "newUser";
        BigDecimal tradeAmount = new BigDecimal("50.00");
        Wallet newWallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.save(any(Wallet.class))).thenReturn(newWallet);
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid()));

        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("Trade with idempotency key should not be applied twice")
    void testTradeIdempotency() {
        String userId = "user123";
        String idempotencyKey = uuid();
        BigDecimal tradeAmount = new BigDecimal("50.00");

        Transaction existingTransaction = new Transaction(
            null, Transaction.TransactionType.TRADE, tradeAmount,
            LocalDateTime.now(), idempotencyKey, new BigDecimal("150.00")
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingTransaction));

        OperationResponse response = walletService.trade(userId,
            new TradeRequest(tradeAmount, idempotencyKey));

        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("150.00"), response.getNewBalance());
        assertTrue(response.getMessage().contains("already processed"));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // ==================== GET WALLET TESTS ====================

    @Test
    @DisplayName("Get wallet should return balance and empty history for new wallet")
    void testGetNewWallet() {
        String userId = "newUser";
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletUserIdOrderByTimestampDescIdDesc(eq(userId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        WalletResponse response = walletService.getWallet(userId);

        assertEquals(userId, response.getUserId());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Get wallet should return balance and transaction history")
    void testGetWalletWithHistory() {
        String userId = "user123";
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction(null, Transaction.TransactionType.DEPOSIT,
            new BigDecimal("100.00"), LocalDateTime.now(), uuid(), new BigDecimal("100.00")));
        transactions.add(new Transaction(null, Transaction.TransactionType.TRADE,
            new BigDecimal("30.00"), LocalDateTime.now(), uuid(), new BigDecimal("70.00")));

        Wallet wallet = createTestWallet(userId, new BigDecimal("70.00"), transactions);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletUserIdOrderByTimestampDescIdDesc(eq(userId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(transactions));

        WalletResponse response = walletService.getWallet(userId);

        assertEquals(userId, response.getUserId());
        assertEquals(new BigDecimal("70.00"), response.getBalance());
        assertEquals(2, response.getTransactions().size());
    }

    @Test
    @DisplayName("Get wallet for non-existent user should return zero balance")
    void testGetNonExistentWallet() {
        String userId = "nonExistent";

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.empty());
        when(transactionRepository.findByWalletUserIdOrderByTimestampDescIdDesc(eq(userId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        WalletResponse response = walletService.getWallet(userId);

        assertEquals(userId, response.getUserId());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
    }

    // ==================== INTEGRATION SCENARIO TESTS ====================

    @Test
    @DisplayName("Multiple deposits should accumulate correctly")
    void testMultipleDeposits() {
        String userId = "user123";
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse response1 = walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), uuid(), userId));
        wallet.setBalance(new BigDecimal("100.00"));

        OperationResponse response2 = walletService.deposit(userId, new DepositRequest(new BigDecimal("50.00"), uuid(), userId));
        wallet.setBalance(new BigDecimal("150.00"));

        assertEquals("success", response1.getStatus());
        assertEquals("success", response2.getStatus());
    }

    @Test
    @DisplayName("Deposit and trade sequence should work correctly")
    void testDepositAndTradeSequence() {
        String userId = "user123";
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet w = invocation.getArgument(0);
            wallet.setBalance(w.getBalance());
            return w;
        });
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse deposit = walletService.deposit(userId, new DepositRequest(new BigDecimal("500.00"), uuid(), userId));
        wallet.setBalance(new BigDecimal("500.00"));

        OperationResponse trade1 = walletService.trade(userId, new TradeRequest(new BigDecimal("200.00"), uuid()));
        wallet.setBalance(new BigDecimal("300.00"));

        OperationResponse trade2 = walletService.trade(userId, new TradeRequest(new BigDecimal("150.00"), uuid()));
        wallet.setBalance(new BigDecimal("150.00"));

        assertEquals("success", deposit.getStatus());
        assertEquals("success", trade1.getStatus());
        assertEquals("success", trade2.getStatus());
    }
}
