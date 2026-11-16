package com.veritasvault.forensicvault.dto.response;

import com.veritasvault.forensicvault.model.enums.LabIntegrityStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForensicLabReportResponse {
    private Long id;
    private Long custodyReservationId;
    private Long evidenceItemId;
    private String evidenceTrackingCode;
    private String evidenceName;
    private Long examinerId;
    private String examinerName;
    private String preExamHash;
    private String postExamHash;
    private LabIntegrityStatus integrityStatus;
    private String examinerFindings;
    private String verifiedByThread;
    private Long verificationDurationMs;
    private LocalDateTime createdAt;
}
