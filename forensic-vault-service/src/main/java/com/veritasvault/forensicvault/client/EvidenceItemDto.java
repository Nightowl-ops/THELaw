package com.veritasvault.forensicvault.client;

import com.veritasvault.forensicvault.model.enums.EvidenceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceItemDto {
    private Long id;
    private String trackingCode;
    private Long caseId;
    private String caseNumber;
    private String name;
    private EvidenceStatus status;
    private String fileStoragePath;
    private String sha256Checksum;
    private Long uploadedById;
    private String uploadedByName;
}
