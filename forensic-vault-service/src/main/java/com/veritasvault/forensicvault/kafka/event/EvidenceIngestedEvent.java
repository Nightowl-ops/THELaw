package com.veritasvault.forensicvault.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceIngestedEvent {
    private Long evidenceId;
    private String itemTrackingCode;
    private Long caseId;
    private String sha256Hash;
    private Long fileSizeBytes;
    private Long uploadedByUserId;
    private LocalDateTime timestamp;
}
