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
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest
@AutoConfigureWebTestClient
@DisplayName("WalletController API Tests")
public class WalletControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletRepository walletRepository;

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
    @DisplayName("POST /wallets/{userId}/deposit should return 200 and success")
    void testDepositEndpoint() {
        String userId = uuid();
        DepositRequest request = new DepositRequest(new BigDecimal("100.00"), uuid(), null);

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("success")
                .jsonPath("$.newBalance").isEqualTo(100.0);
    }

    @Test
    @DisplayName("POST /wallets/{userId}/trade should return 200 for valid trade")
    void testTradeEndpointWithSufficientBalance() {
        String userId = uuid();

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DepositRequest(new BigDecimal("500.00"), uuid(), null))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/wallets/{userId}/trade", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TradeRequest(new BigDecimal("200.00"), uuid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("success")
                .jsonPath("$.newBalance").isEqualTo(300.0);
    }

    @Test
    @DisplayName("POST /wallets/{userId}/trade should return 400 for insufficient balance")
    void testTradeEndpointWithInsufficientBalance() {
        String userId = uuid();

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DepositRequest(new BigDecimal("50.00"), uuid(), null))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/wallets/{userId}/trade", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TradeRequest(new BigDecimal("100.00"), uuid()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("error")
                .jsonPath("$.message").value(msg -> msg.toString().contains("Insufficient balance"));
    }

    @Test
    @DisplayName("GET /wallets/{userId} should return wallet balance and history")
    void testGetWalletEndpoint() {
        String userId = uuid();

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DepositRequest(new BigDecimal("200.00"), uuid(), null))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/wallets/{userId}/trade", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TradeRequest(new BigDecimal("50.00"), uuid()))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/wallets/{userId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.walletId").isNotEmpty()
                .jsonPath("$.userId").isEqualTo(userId)
                .jsonPath("$.balance").isEqualTo(150.0)
                .jsonPath("$.transactions.length()").isEqualTo(2)
                .jsonPath("$.transactions[0].type").isEqualTo("TRADE")
                .jsonPath("$.transactions[1].type").isEqualTo("DEPOSIT");
    }

    @Test
    @DisplayName("GET /wallets/{userId} for non-existent user should return zero balance")
    void testGetWalletForNonExistentUser() {
        String userId = uuid();

        webTestClient.get().uri("/wallets/{userId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(userId)
                .jsonPath("$.balance").isEqualTo(0)
                .jsonPath("$.transactions.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("Idempotency: Retried deposit should not duplicate")
    void testDepositIdempotencyViaAPI() {
        String userId = uuid();
        String idempotencyKey = uuid();
        DepositRequest request = new DepositRequest(new BigDecimal("100.00"), idempotencyKey, null);

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(100.0);

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(100.0);

        webTestClient.get().uri("/wallets/{userId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(100.0)
                .jsonPath("$.transactions.length()").isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency: Retried trade should not duplicate")
    void testTradeIdempotencyViaAPI() {
        String userId = uuid();

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DepositRequest(new BigDecimal("500.00"), uuid(), null))
                .exchange()
                .expectStatus().isOk();

        String tradeKey = uuid();
        TradeRequest tradeRequest = new TradeRequest(new BigDecimal("100.00"), tradeKey);

        webTestClient.post().uri("/wallets/{userId}/trade", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tradeRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(400.0);

        webTestClient.post().uri("/wallets/{userId}/trade", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tradeRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(400.0);

        webTestClient.get().uri("/wallets/{userId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(400.0)
                .jsonPath("$.transactions.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("Invalid deposit amount should return 400")
    void testInvalidDepositAmount() {
        String userId = uuid();
        DepositRequest request = new DepositRequest(new BigDecimal("-50.00"), uuid(), null);

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    @DisplayName("Invalid idempotency key should return validation error")
    void testInvalidIdempotencyKey() {
        String userId = uuid();
        DepositRequest request = new DepositRequest(new BigDecimal("50.00"), "not-a-uuid-v4", null);

        webTestClient.post().uri("/wallets/{userId}/deposit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Validation Failed")
                .jsonPath("$.details").value(d -> d.toString().contains("UUID v4"));
    }
}
