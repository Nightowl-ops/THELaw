package com.veritasvault.caseevidence.kafka;

import com.veritasvault.caseevidence.kafka.event.EvidenceIngestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EvidenceKafkaProducer {

    public static final String EVIDENCE_INGESTED_TOPIC = "evidence-ingested-topic";

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvidenceIngested(EvidenceIngestedEvent event) {
        if (kafkaTemplate != null) {
            try {
                log.info("Publishing evidence ingested event to Kafka: {}", event.getItemTrackingCode());
                kafkaTemplate.send(EVIDENCE_INGESTED_TOPIC, event.getEvidenceId().toString(), event);
            } catch (Exception e) {
                log.warn("Kafka not reachable to publish evidence-ingested event: {}", e.getMessage());
            }
        } else {
            log.debug("KafkaTemplate not configured, skipping Kafka broadcast for evidence: {}", event.getItemTrackingCode());
        }
    }
}
