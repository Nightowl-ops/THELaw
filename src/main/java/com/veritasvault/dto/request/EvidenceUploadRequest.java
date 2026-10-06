package com.veritasvault.dto.request;

import com.veritasvault.model.enums.EvidenceType;
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
public class EvidenceUploadRequest {

    @NotNull(message = "Case ID is required")
    private Long caseId;

    @NotBlank(message = "Evidence item name is required")
    @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
    private String name;

    private String description;

    @NotNull(message = "Evidence type is required (e.g., VIDEO, AUDIO, DOCUMENT, DEVICE_IMAGE, NETWORK_LOG)")
    private EvidenceType evidenceType;

    @NotBlank(message = "Source origin is required (e.g., 'Officer Bodycam #4', 'iPhone 15 Extraction')")
    private String sourceOrigin;

    private String seizingOfficerName;

    private String officerBadgeNumber;

    private String lawEnforcementAgency;

    private String policeIncidentReportNum;

    private LocalDateTime collectedAt;
}