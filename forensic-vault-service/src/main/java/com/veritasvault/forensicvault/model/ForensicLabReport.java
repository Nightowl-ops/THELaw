package com.veritasvault.forensicvault.model;

import com.veritasvault.forensicvault.model.enums.LabIntegrityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forensic_lab_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForensicLabReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custody_reservation_id", nullable = false, unique = true)
    private CustodyReservation custodyReservation;

    @Column(name = "evidence_item_id", nullable = false)
    private Long evidenceItemId;

    @Column(name = "examiner_id", nullable = false)
    private Long examinerId;

    @Column(name = "pre_exam_hash", nullable = false, length = 64)
    private String preExamHash;

    @Column(name = "post_exam_hash", nullable = false, length = 64)
    private String postExamHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_status", nullable = false, length = 30)
    private LabIntegrityStatus integrityStatus;

    @Column(name = "examiner_findings", columnDefinition = "TEXT")
    private String examinerFindings;

    @Column(name = "verified_by_thread", length = 100)
    private String verifiedByThread;

    @Column(name = "verification_duration_ms")
    private Long verificationDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
