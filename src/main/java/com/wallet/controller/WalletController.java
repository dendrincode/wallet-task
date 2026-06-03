package com.wallet.controller;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.OperationResponse;
import com.wallet.dto.TradeRequest;
import com.wallet.dto.TransactionPageResponse;
import com.wallet.dto.UpdateWalletRequest;
import com.wallet.dto.WalletResponse;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Validated
public class WalletController {

    private final WalletService walletService;

    /**
     * Deposit funds into a wallet.
     * POST /wallets/{userId}/deposit
     * userId must be a UUID v4 format
     */
    @PostMapping("/{userId}/deposit")
    public ResponseEntity<OperationResponse> deposit(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId,
            @Valid @RequestBody DepositRequest request) {
        OperationResponse response = walletService.deposit(userId, request);
        HttpStatus status = "success".equals(response.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(response, status);
    }

    /**
     * Place a trade (debit funds from wallet).
     * POST /wallets/{userId}/trade
     * userId must be a UUID v4 format
     */
    @PostMapping("/{userId}/trade")
    public ResponseEntity<OperationResponse> trade(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId,
            @Valid @RequestBody TradeRequest request) {
        OperationResponse response = walletService.trade(userId, request);
        HttpStatus status = "success".equals(response.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(response, status);
    }

    /**
     * Get current wallet balance and transaction history.
     * GET /wallets/{userId}
     * userId must be a UUID v4 format
     */
    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId) {
        WalletResponse response = walletService.getWallet(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Get paginated transaction history for a wallet.
     * GET /wallets/{userId}/transactions?page=0&size=20
     */
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(walletService.getTransactions(userId, page, size));
    }

    /**
     * Freeze a wallet, preventing any further deposits or trades.
     * POST /wallets/{userId}/freeze
     */
    @PostMapping("/{userId}/freeze")
    public ResponseEntity<WalletResponse> freezeWallet(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId) {
        WalletResponse response = walletService.freezeWallet(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Close a wallet permanently.
     * POST /wallets/{userId}/close
     */
    @PostMapping("/{userId}/close")
    public ResponseEntity<WalletResponse> closeWallet(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId) {
        WalletResponse response = walletService.closeWallet(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Update wallet information (description).
     * PATCH /wallets/{userId}
     * userId must be a UUID v4 format
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<WalletResponse> updateWallet(
            @PathVariable(value = "userId") @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "userId must be a UUID v4"
            ) String userId,
            @Valid @RequestBody UpdateWalletRequest request) {
        WalletResponse response = walletService.updateWallet(userId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
