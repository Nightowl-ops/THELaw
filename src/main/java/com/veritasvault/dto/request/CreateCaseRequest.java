package com.veritasvault.dto.request;

import com.veritasvault.model.enums.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

/// this is used to create the payload sent when creating a new case , validating  that required fileds
/// are present beofre hitting the database

public class CreateCaseRequest {

    @NotBlank(message = "Case number is required (e.g., CR-2026-0041)")
    private String caseNumber;

    @NotBlank(message = "Case title is required")
    private String caseTitle;

    @NotNull(message = "Case type is required (DEFENSE or PROSECUTION)")
    private CaseType caseType;

    @NotNull(message = "Lead attorney ID is required")
    private Long leadAttorneyId;

    private Long clientId;

    private String opposingPartyName;

    private String opposingCounselName;

    private String opposingCounselEmail;
}