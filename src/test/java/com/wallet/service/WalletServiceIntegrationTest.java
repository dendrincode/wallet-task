package com.wallet.service;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.TradeRequest;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("WalletService Integration Tests")
public class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll()
                .then(walletRepository.deleteAll())
                .then(userRepository.deleteAll())
                .block();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("Integration: Deposit and retrieve wallet")
    void testDepositAndGetWallet() {
        String userId = uuid();

        walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), uuid(), null)).block();
        var response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(new BigDecimal("100.00"), response.getBalance());
        assertEquals(1, response.getTransactions().size());
    }

    @Test
    @DisplayName("Integration: Multiple operations with history")
    void testMultipleOperationsWithHistory() {
        String userId = uuid();

        walletService.deposit(userId, new DepositRequest(new BigDecimal("1000.00"), uuid(), null)).block();
        walletService.trade(userId, new TradeRequest(new BigDecimal("250.00"), uuid())).block();
        walletService.deposit(userId, new DepositRequest(new BigDecimal("500.00"), uuid(), null)).block();
        walletService.trade(userId, new TradeRequest(new BigDecimal("100.00"), uuid())).block();

        var response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(new BigDecimal("1150.00"), response.getBalance());
        assertEquals(4, response.getTransactions().size());
    }

    @Test
    @DisplayName("Integration: Idempotency prevents duplicate operations")
    void testIdempotencyPreventsDoubles() {
        String userId = uuid();
        String idempotencyKey = uuid();

        var deposit1 = walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), idempotencyKey, null)).block();
        var deposit2 = walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), idempotencyKey, null)).block();
        var response = walletService.getWallet(userId).block();

        assertNotNull(deposit1);
        assertNotNull(deposit2);
        assertNotNull(response);
        assertEquals(deposit1.getNewBalance(), deposit2.getNewBalance());
        assertEquals(1, response.getTransactions().size());
    }

    @Test
    @DisplayName("Integration: Trade prevents negative balance")
    void testTradePreventingNegativeBalance() {
        String userId = uuid();

        walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"), uuid(), null)).block();
        var tradeResponse = walletService.trade(userId, new TradeRequest(new BigDecimal("200.00"), uuid())).block();
        var walletResponse = walletService.getWallet(userId).block();

        assertNotNull(tradeResponse);
        assertNotNull(walletResponse);
        assertEquals("error", tradeResponse.getStatus());
        assertEquals(new BigDecimal("100.00"), walletResponse.getBalance());
        assertEquals(1, walletResponse.getTransactions().size());
    }

    @Test
    @DisplayName("Integration: Wallet starts at zero for new users")
    void testNewWalletStartsAtZero() {
        String userId = uuid();

        var response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Integration: Same idempotency key used by two users is treated independently")
    void testIdempotencyKeyIsScopedToUser() {
        String userA = uuid();
        String userB = uuid();
        String sharedKey = uuid();

        var responseA = walletService.deposit(userA, new DepositRequest(new BigDecimal("100.00"), sharedKey, null)).block();
        assertNotNull(responseA);
        assertEquals("success", responseA.getStatus());
        assertEquals(new BigDecimal("100.00"), responseA.getNewBalance());

        var responseB = walletService.deposit(userB, new DepositRequest(new BigDecimal("50.00"), sharedKey, null)).block();
        assertNotNull(responseB);
        assertEquals("success", responseB.getStatus());
        assertEquals(new BigDecimal("50.00"), responseB.getNewBalance());

        assertEquals(1, walletService.getTransactions(userA, 0, 10).block().getTotalElements());
        assertEquals(1, walletService.getTransactions(userB, 0, 10).block().getTotalElements());
    }

    @Test
    @DisplayName("Integration: Large number of operations")
    void testManyOperations() {
        String userId = uuid();

        BigDecimal balance = BigDecimal.ZERO;
        for (int i = 0; i < 100; i++) {
            balance = balance.add(new BigDecimal("10.00"));
            walletService.deposit(userId, new DepositRequest(new BigDecimal("10.00"), uuid(), null)).block();
        }
        for (int i = 0; i < 50; i++) {
            balance = balance.subtract(new BigDecimal("5.00"));
            walletService.trade(userId, new TradeRequest(new BigDecimal("5.00"), uuid())).block();
        }

        var response = walletService.getWallet(userId).block();

        assertNotNull(response);
        assertEquals(balance, response.getBalance());
        assertEquals(10, response.getTransactions().size());
        assertEquals(150, walletService.getTransactions(userId, 0, 150).block().getTotalElements());
    }
}
