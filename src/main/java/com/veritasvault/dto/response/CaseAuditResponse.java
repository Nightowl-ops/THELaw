package com.veritasvault.dto.response;

import com.veritasvault.model.enums.LabIntegrityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseAuditResponse {
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private int totalEvidenceAudited;
    private int verifiedCount;
    private int compromisedCount;
    private long totalDurationMs;
    private List<EvidenceAuditItem> auditItems;
    private LocalDateTime auditedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvidenceAuditItem {
        private Long evidenceId;
        private String trackingCode;
        private String name;
        private String baselineHash;
        private String computedHash;
        private LabIntegrityStatus status;
        private String verifiedByThread;
        private long durationMs;
    }
}
