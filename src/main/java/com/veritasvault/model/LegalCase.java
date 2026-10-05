package com.veritasvault.model;

import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", nullable = false, unique = true)
    private String caseNumber;

    @Column(name = "case_title", nullable = false)
    private String caseTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false)
    private CaseType caseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_attorney_id", nullable = false)
    private User leadAttorney;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    @Column(name = "opposing_party_name")
    private String opposingPartyName;

    @Column(name = "opposing_counsel_name")
    private String opposingCounselName;

    @Column(name = "opposing_counsel_email")
    private String opposingCounselEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CaseStatus status = CaseStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}