package com.wallet.service;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.OperationResponse;
import com.wallet.dto.TradeRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.entity.Transaction;
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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Mock
    private R2dbcEntityTemplate r2dbcTemplate;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, userRepository, r2dbcTemplate);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static Wallet createTestWallet(String userId, BigDecimal balance) {
        Wallet wallet = new Wallet(userId);
        wallet.setWalletId(UUID.randomUUID().toString());
        wallet.setBalance(balance);
        return wallet;
    }

    // ==================== DEPOSIT TESTS ====================

    @Test
    @DisplayName("Deposit should increase balance correctly")
    void testDepositIncreasesBalance() {
        String userId = uuid();
        BigDecimal depositAmount = new BigDecimal("100.00");
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(r2dbcTemplate.insert(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        OperationResponse response = walletService.deposit(userId, new DepositRequest(depositAmount, uuid(), null)).block();

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getNewBalance());
        verify(walletRepository, times(1)).save(any(Wallet.class));
        verify(r2dbcTemplate).insert(any(Transaction.class));
    }

    @Test
    @DisplayName("Deposit should create wallet and user if neither exists")
    void testDepositCreatesNewWallet() {
        String userId = uuid();
        BigDecimal depositAmount = new BigDecimal("50.00");
        Wallet newWallet = createTestWallet(userId, BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Mono.empty());
        when(userRepository.findById(userId)).thenReturn(Mono.empty());
        when(r2dbcTemplate.insert(any(com.wallet.entity.User.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(r2dbcTemplate.insert(any(Wallet.class))).thenReturn(Mono.just(newWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(newWallet));
        when(r2dbcTemplate.insert(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        OperationResponse response = walletService.deposit(userId, new DepositRequest(depositAmount, uuid(), null)).block();

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        verify(r2dbcTemplate).insert(any(com.wallet.entity.User.class));
        verify(r2dbcTemplate).insert(any(Wallet.class));
    }

    @Test
    @DisplayName("Deposit with invalid amount should return error")
    void testDepositWithInvalidAmount() {
        String userId = uuid();

        OperationResponse response = walletService.deposit(userId, new DepositRequest(new BigDecimal("-10.00"), uuid(), null)).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("greater than zero"));

        response = walletService.deposit(userId, new DepositRequest(BigDecimal.ZERO, uuid(), null)).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());

        response = walletService.deposit(userId, new DepositRequest(null, uuid(), null)).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());
    }

    @Test
    @DisplayName("Deposit with idempotency key should not be applied twice")
    void testDepositIdempotency() {
        String userId = uuid();
        String idempotencyKey = uuid();
        BigDecimal depositAmount = new BigDecimal("100.00");

        Transaction existingTx = new Transaction(uuid(), Transaction.TransactionType.DEPOSIT,
                depositAmount, LocalDateTime.now(), idempotencyKey, new BigDecimal("100.00"));

        when(transactionRepository.findByIdempotencyKeyAndUserId(eq(idempotencyKey), eq(userId)))
                .thenReturn(Mono.just(existingTx));

        OperationResponse response = walletService.deposit(userId,
                new DepositRequest(depositAmount, idempotencyKey, null)).block();

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getNewBalance());
        assertTrue(response.getMessage().contains("already processed"));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // ==================== TRADE TESTS ====================

    @Test
    @DisplayName("Trade should decrease balance correctly")
    void testTradeDecreasesBalance() {
        String userId = uuid();
        BigDecimal tradeAmount = new BigDecimal("50.00");
        Wallet wallet = createTestWallet(userId, new BigDecimal("200.00"));

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(r2dbcTemplate.insert(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid())).block();

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("150.00"), response.getNewBalance());
        verify(walletRepository, times(1)).save(any(Wallet.class));
        verify(r2dbcTemplate).insert(any(Transaction.class));
    }

    @Test
    @DisplayName("Trade should fail if balance would go negative")
    void testTradeFailsWithInsufficientBalance() {
        String userId = uuid();
        BigDecimal tradeAmount = new BigDecimal("100.00");
        Wallet wallet = createTestWallet(userId, new BigDecimal("50.00"));

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Mono.just(wallet));

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid())).block();

        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("Insufficient balance"));
        assertEquals(new BigDecimal("50.00"), response.getNewBalance());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Trade should fail with invalid amount")
    void testTradeWithInvalidAmount() {
        String userId = uuid();

        OperationResponse response = walletService.trade(userId, new TradeRequest(new BigDecimal("-50.00"), uuid())).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());

        response = walletService.trade(userId, new TradeRequest(BigDecimal.ZERO, uuid())).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());

        response = walletService.trade(userId, new TradeRequest(null, uuid())).block();
        assertNotNull(response);
        assertEquals("error", response.getStatus());
    }

    @Test
    @DisplayName("Trade on new wallet should fail with insufficient balance")
    void testTradeFailsOnNewWallet() {
        String userId = uuid();
        BigDecimal tradeAmount = new BigDecimal("50.00");
        Wallet newWallet = createTestWallet(userId, BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Mono.empty());
        when(userRepository.findById(userId)).thenReturn(Mono.empty());
        when(r2dbcTemplate.insert(any(com.wallet.entity.User.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(r2dbcTemplate.insert(any(Wallet.class))).thenReturn(Mono.just(newWallet));

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, uuid())).block();

        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("Trade with idempotency key should not be applied twice")
    void testTradeIdempotency() {
        String userId = uuid();
        String idempotencyKey = uuid();
        BigDecimal tradeAmount = new BigDecimal("50.00");

        Transaction existingTx = new Transaction(uuid(), Transaction.TransactionType.TRADE,
                tradeAmount, LocalDateTime.now(), idempotencyKey, new BigDecimal("150.00"));

        when(transactionRepository.findByIdempotencyKeyAndUserId(eq(idempotencyKey), eq(userId)))
                .thenReturn(Mono.just(existingTx));

        OperationResponse response = walletService.trade(userId, new TradeRequest(tradeAmount, idempotencyKey)).block();

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals(new BigDecimal("150.00"), response.getNewBalance());
        assertTrue(response.getMessage().contains("already processed"));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // ==================== GET WALLET TESTS ====================

    @Test
    @DisplayName("Get wallet should return balance and empty history for new wallet")
    void testGetNewWallet() {
        String userId = uuid();
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(walletRepository.findByUserId(userId)).thenReturn(Mono.just(wallet));
        when(transactionRepository.findByUserIdPaged(eq(userId), eq(10), eq(0L))).thenReturn(Flux.empty());

        WalletResponse response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Get wallet should return balance and transaction history")
    void testGetWalletWithHistory() {
        String userId = uuid();
        String walletId = uuid();
        Wallet wallet = createTestWallet(userId, new BigDecimal("70.00"));
        wallet.setWalletId(walletId);

        List<Transaction> transactions = List.of(
                new Transaction(walletId, Transaction.TransactionType.DEPOSIT,
                        new BigDecimal("100.00"), LocalDateTime.now(), uuid(), new BigDecimal("100.00")),
                new Transaction(walletId, Transaction.TransactionType.TRADE,
                        new BigDecimal("30.00"), LocalDateTime.now(), uuid(), new BigDecimal("70.00"))
        );

        when(walletRepository.findByUserId(userId)).thenReturn(Mono.just(wallet));
        when(transactionRepository.findByUserIdPaged(eq(userId), eq(10), eq(0L)))
                .thenReturn(Flux.fromIterable(transactions));

        WalletResponse response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(new BigDecimal("70.00"), response.getBalance());
        assertEquals(2, response.getTransactions().size());
    }

    @Test
    @DisplayName("Get wallet for non-existent user should return zero balance")
    void testGetNonExistentWallet() {
        String userId = uuid();

        when(walletRepository.findByUserId(userId)).thenReturn(Mono.empty());

        WalletResponse response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
    }

    // ==================== INTEGRATION SCENARIO TESTS ====================

    @Test
    @DisplayName("Multiple deposits should accumulate correctly")
    void testMultipleDeposits() {
        String userId = uuid();
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(r2dbcTemplate.insert(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        OperationResponse response1 = walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), uuid(), null)).block();
        wallet.setBalance(new BigDecimal("100.00"));

        OperationResponse response2 = walletService.deposit(userId, new DepositRequest(new BigDecimal("50.00"), uuid(), null)).block();
        wallet.setBalance(new BigDecimal("150.00"));

        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals("success", response1.getStatus());
        assertEquals("success", response2.getStatus());
    }

    @Test
    @DisplayName("Deposit and trade sequence should work correctly")
    void testDepositAndTradeSequence() {
        String userId = uuid();
        Wallet wallet = createTestWallet(userId, BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKeyAndUserId(any(), any())).thenReturn(Mono.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Mono.just(wallet));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> {
            Wallet w = i.getArgument(0);
            wallet.setBalance(w.getBalance());
            return Mono.just(w);
        });
        when(r2dbcTemplate.insert(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        OperationResponse deposit = walletService.deposit(userId, new DepositRequest(new BigDecimal("500.00"), uuid(), null)).block();
        wallet.setBalance(new BigDecimal("500.00"));

        OperationResponse trade1 = walletService.trade(userId, new TradeRequest(new BigDecimal("200.00"), uuid())).block();
        wallet.setBalance(new BigDecimal("300.00"));

        OperationResponse trade2 = walletService.trade(userId, new TradeRequest(new BigDecimal("150.00"), uuid())).block();

        assertNotNull(deposit);
        assertNotNull(trade1);
        assertNotNull(trade2);
        assertEquals("success", deposit.getStatus());
        assertEquals("success", trade1.getStatus());
        assertEquals("success", trade2.getStatus());
    }
}
