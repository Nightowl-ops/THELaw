package com.veritasvault.forensicvault.kafka;

import com.veritasvault.forensicvault.kafka.event.EvidenceIngestedEvent;
import com.veritasvault.forensicvault.kafka.event.UserDeactivatedEvent;
import com.veritasvault.forensicvault.model.ChainOfCustodyLog;
import com.veritasvault.forensicvault.model.CustodyReservation;
import com.veritasvault.forensicvault.model.enums.CustodyActionType;
import com.veritasvault.forensicvault.model.enums.ReservationStatus;
import com.veritasvault.forensicvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.forensicvault.repository.CustodyReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class VaultKafkaConsumer {

    private final CustodyReservationRepository reservationRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;

    @KafkaListener(topics = "user-deactivated-topic", groupId = "forensic-vault-group")
    @Transactional
    public void handleUserDeactivated(UserDeactivatedEvent event) {
        log.warn("🚨 KAFKA ALERT: Received User Deactivated Event for User ID: {} ({})", event.getUserId(), event.getEmail());

        List<CustodyReservation> activeReservations = reservationRepository.findByExaminerIdAndStatusIn(
                event.getUserId(),
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
        );

        for (CustodyReservation res : activeReservations) {
            res.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(res);

            ChainOfCustodyLog cancellationLog = ChainOfCustodyLog.builder()
                    .evidenceId(res.getEvidenceId())
                    .actorUserId(event.getUserId())
                    .actionType(CustodyActionType.CHECK_IN)
                    .notes("Reservation #" + res.getId() + " automatically cancelled: Assigned examiner was deactivated.")
                    .hashAtEvent("N/A")
                    .build();
            custodyLogRepository.save(cancellationLog);

            log.info("Cancelled reservation #{} for deactivated examiner #{}", res.getId(), event.getUserId());
        }
    }

    @KafkaListener(topics = "evidence-ingested-topic", groupId = "forensic-vault-group")
    @Transactional
    public void handleEvidenceIngested(EvidenceIngestedEvent event) {
        log.info("KAFKA CONSUMER: Received Evidence Ingested Event for Evidence ID: {} [{}]",
                event.getEvidenceId(), event.getItemTrackingCode());

        ChainOfCustodyLog intakeLog = ChainOfCustodyLog.builder()
                .evidenceId(event.getEvidenceId())
                .actorUserId(event.getUploadedByUserId() != null ? event.getUploadedByUserId() : 1L)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Evidence intake completed. Cryptographic SHA-256 fingerprint registered: " + event.getSha256Hash())
                .hashAtEvent(event.getSha256Hash())
                .build();

        custodyLogRepository.save(intakeLog);
        log.info("Initialized Chain of Custody ledger for newly ingested evidence item #{}", event.getEvidenceId());
    }
}
