package com.veritasvault.dto.response;

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
public class DiscoveryResponse {

    private Long id;
    private Long caseId;
    private String caseNumber;
    private String title;
    private String description;
    private String accessToken;
    private String downloadUrl;
    private String recipientParty;
    private String recipientEmail;
    private LocalDateTime expiresAt;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Boolean isRevoked;
    private Long createdById;
    private String createdByName;
    private List<EvidenceResponse> evidenceItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}