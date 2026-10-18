package com.veritasvault.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDiscoveryRequest {

    @NotNull(message = "Case ID is required")
    private Long caseId;

    @NotBlank(message = "Recipient party name is required (e.g., 'Opposing Counsel - Smith & Co')")
    @Size(max = 150, message = "Recipient party cannot exceed 150 characters")
    private String recipientParty;

    @NotNull(message = "Expiration timestamp is required")
    @Future(message = "Expiration timestamp must be in the future")
    private LocalDateTime expiresAt;
}