package com.veritasvault.dto.response;

import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceResponse {

    private Long id;
    private String trackingCode;
    private Long caseId;
    private String caseNumber;
    private String name;
    private String description;
    private EvidenceType evidenceType;
    private EvidenceStatus status;
    private String fileStoragePath;
    private String originalFileName;
    private String mimeType;
    private Long fileSizeBytes;
    private String sha256Checksum;
    private String sourceOrigin;
    private String seizingOfficerName;
    private String officerBadgeNumber;
    private String lawEnforcementAgency;
    private String policeIncidentReportNum;
    private Long uploadedById;
    private String uploadedByName;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}