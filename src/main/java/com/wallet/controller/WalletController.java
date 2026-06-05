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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Validated
public class WalletController {

    private static final String UUID_V4_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private final WalletService walletService;

    @PostMapping("/{userId}/deposit")
    public Mono<ResponseEntity<OperationResponse>> deposit(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId,
            @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(userId, request)
                .map(r -> ResponseEntity.status("success".equals(r.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(r));
    }

    @PostMapping("/{userId}/trade")
    public Mono<ResponseEntity<OperationResponse>> trade(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId,
            @Valid @RequestBody TradeRequest request) {
        return walletService.trade(userId, request)
                .map(r -> ResponseEntity.status("success".equals(r.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(r));
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<WalletResponse>> getWallet(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId) {
        return walletService.getWallet(userId)
                .map(r -> ResponseEntity.ok(r));
    }

    @GetMapping("/{userId}/transactions")
    public Mono<ResponseEntity<TransactionPageResponse>> getTransactions(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return walletService.getTransactions(userId, page, size)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{userId}/freeze")
    public Mono<ResponseEntity<WalletResponse>> freezeWallet(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId) {
        return walletService.freezeWallet(userId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{userId}/close")
    public Mono<ResponseEntity<WalletResponse>> closeWallet(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId) {
        return walletService.closeWallet(userId)
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{userId}")
    public Mono<ResponseEntity<WalletResponse>> updateWallet(
            @PathVariable(value = "userId") @Pattern(regexp = UUID_V4_PATTERN, message = "userId must be a UUID v4") String userId,
            @Valid @RequestBody UpdateWalletRequest request) {
        return walletService.updateWallet(userId, request)
                .map(ResponseEntity::ok);
    }
}
