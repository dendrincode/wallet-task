package com.wallet.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating wallet information.
 * Currently supports updating wallet description.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWalletRequest {
    /**
     * Optional short description for the wallet.
     * Maximum 255 characters. Used to document wallet purpose/details.
     * Set to empty string or null to clear existing description.
     */
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
