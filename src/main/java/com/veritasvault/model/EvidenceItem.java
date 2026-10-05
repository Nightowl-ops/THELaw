package com.veritasvault.model;

import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private LegalCase legalCase;

    @Column(name = "item_tracking_code", nullable = false, unique = true)
    private String itemTrackingCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private EvidenceType evidenceType;

    @Column(name = "source_origin", nullable = false)
    private String sourceOrigin;

    @Column(name = "seizing_officer_name")
    private String seizingOfficerName;

    @Column(name = "officer_badge_number")
    private String officerBadgeNumber;

    @Column(name = "law_enforcement_agency")
    private String lawEnforcementAgency;

    @Column(name = "police_incident_report_num")
    private String policeIncidentReportNum;

    @Column(name = "storage_url", nullable = false)
    private String storageUrl;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EvidenceStatus status = EvidenceStatus.INGESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}