package com.veritasvault.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDiscoveryRequest {

    @NotNull(message = "Case ID is required")
    private Long caseId;

    @NotBlank(message = "Production title is required")
    @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
    private String title;

    private String description;

    @NotBlank(message = "Recipient party name is required (e.g., 'Opposing Counsel - Smith & Co')")
    @Size(max = 150, message = "Recipient party cannot exceed 150 characters")
    private String recipientParty;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email must be a valid email format")
    private String recipientEmail;

    @NotNull(message = "Expiration timestamp is required")
    @Future(message = "Expiration timestamp must be in the future")
    private LocalDateTime expiresAt;

    @Min(value = 1, message = "Maximum downloads must be at least 1")
    private Integer maxDownloads;

    @NotEmpty(message = "At least one evidence item ID must be included in the discovery package")
    private List<Long> evidenceItemIds;
}