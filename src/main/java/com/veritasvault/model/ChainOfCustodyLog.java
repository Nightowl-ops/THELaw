package com.veritasvault.model;

import com.veritasvault.model.enums.CustodyActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chain_of_custody_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChainOfCustodyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    private EvidenceItem evidenceItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private CustodyActionType actionType;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes;

    @Column(name = "hash_at_event", nullable = false)
    private String hashAtEvent;

    @CreationTimestamp
    @Column(name = "logged_at", updatable = false)
    private LocalDateTime loggedAt;
}