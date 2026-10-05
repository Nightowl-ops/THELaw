package com.veritasvault.dto.response;

import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseResponse {

    private Long id;
    private String caseNumber;
    private String caseTitle;
    private CaseType caseType;
    private CaseStatus status;

    // Lead Attorney Summary
    private Long leadAttorneyId;
    private String leadAttorneyName;
    private String leadAttorneyEmail;

    // Client Summary (if assigned)
    private Long clientId;
    private String clientName;
    private String clientEmail;

    private String opposingPartyName;
    private String opposingCounselName;
    private String opposingCounselEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}