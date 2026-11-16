package com.veritasvault.caseevidence.model;

import com.veritasvault.caseevidence.model.enums.BinderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exhibit_binders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExhibitBinder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private LegalCase legalCase;

    @Column(name = "binder_name", nullable = false)
    private String binderName;

    @Column(name = "bates_prefix", nullable = false)
    private String batesPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BinderStatus status = BinderStatus.DRAFT;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
