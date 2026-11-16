package com.veritasvault.caseevidence.kafka;

import com.veritasvault.caseevidence.kafka.event.EvidenceTamperedEvent;
import com.veritasvault.caseevidence.model.EvidenceItem;
import com.veritasvault.caseevidence.model.enums.EvidenceStatus;
import com.veritasvault.caseevidence.repository.EvidenceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class EvidenceKafkaConsumer {

    private final EvidenceItemRepository evidenceItemRepository;

    @KafkaListener(topics = "evidence-tampered-topic", groupId = "case-evidence-group")
    @Transactional
    public void handleEvidenceTampered(EvidenceTamperedEvent event) {
        log.warn("🚨 KAFKA ALERT: Received Evidence Tampered Event for Evidence ID: {}", event.getEvidenceId());
        evidenceItemRepository.findById(event.getEvidenceId()).ifPresentOrElse(evidence -> {
            evidence.setStatus(EvidenceStatus.SEQUESTERED);
            evidenceItemRepository.save(evidence);
            log.info("Evidence item #{} [{}] successfully marked as SEQUESTERED (Quarantine)",
                    evidence.getId(), evidence.getItemTrackingCode());
        }, () -> log.error("Evidence item ID {} not found for tampering quarantine", event.getEvidenceId()));
    }
}
