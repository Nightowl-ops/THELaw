package com.veritasvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "discovery_productions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private LegalCase legalCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binder_id", nullable = false)
    private ExhibitBinder binder;

    @Column(name = "target_party_name", nullable = false)
    private String targetPartyName;

    @Column(name = "access_token", nullable = false, unique = true)
    private String accessToken;

    @Column(name = "download_password_hash")
    private String downloadPasswordHash;

    @Column(name = "is_watermarked", nullable = false)
    @Builder.Default
    private Boolean isWatermarked = true;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}