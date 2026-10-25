package com.veritasvault.dto.response;

import com.veritasvault.model.enums.LabIntegrityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
