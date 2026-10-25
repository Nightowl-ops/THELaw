package com.veritasvault.model;

import com.veritasvault.model.enums.LabIntegrityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_item_id", nullable = false)
    private EvidenceItem evidenceItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examiner_id", nullable = false)
    private User examiner;

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
