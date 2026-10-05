package com.veritasvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "binder_exhibits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinderExhibit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binder_id", nullable = false)
    private ExhibitBinder binder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    private EvidenceItem evidenceItem;

    @Column(name = "exhibit_number", nullable = false)
    private String exhibitNumber;

    @Column(name = "bates_start_num", nullable = false)
    private Integer batesStartNum;

    @Column(name = "bates_end_num", nullable = false)
    private Integer batesEndNum;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;
}