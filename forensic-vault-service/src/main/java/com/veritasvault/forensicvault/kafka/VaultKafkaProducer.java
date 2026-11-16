package com.veritasvault.forensicvault.kafka;

import com.veritasvault.forensicvault.kafka.event.EvidenceTamperedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class VaultKafkaProducer {

    private static final String TOPIC_EVIDENCE_TAMPERED = "evidence-tampered-topic";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvidenceTampered(Long evidenceId, String expectedHash, String actualHash, Long examinerId, String reason) {
        EvidenceTamperedEvent event = EvidenceTamperedEvent.builder()
                .evidenceId(evidenceId)
                .expectedHash(expectedHash)
                .actualHash(actualHash)
                .examinerId(examinerId)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();

        log.warn("🚨 KAFKA PRODUCER: Emitting Evidence Tampered alert for evidence ID: {}", evidenceId);
        kafkaTemplate.send(TOPIC_EVIDENCE_TAMPERED, String.valueOf(evidenceId), event);
    }
}
