package com.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    /**
     * UUID v4 format idempotency key for request deduplication.
     * Pattern: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
     * Note: Path parameter userId must also be UUID v4 format
     */
    @NotNull(message = "Idempotency key is required")
    @Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        message = "Idempotency key must be a UUID v4"
    )
    private String idempotencyKey;

    /**
     * Optional short description for the wallet.
     * Maximum 255 characters. Typically used on wallet creation (first deposit).
     */
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
