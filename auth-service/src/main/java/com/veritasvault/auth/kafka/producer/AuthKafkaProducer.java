package com.veritasvault.auth.kafka.producer;

import com.veritasvault.auth.kafka.event.UserDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(AuthKafkaProducer.class);
    private static final String TOPIC_USER_DEACTIVATED = "user-deactivated-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeactivatedEvent(UserDeactivatedEvent event) {
        log.info("Publishing UserDeactivatedEvent to Kafka topic [{}]: User ID = {}",
                TOPIC_USER_DEACTIVATED, event.getUserId());
        kafkaTemplate.send(TOPIC_USER_DEACTIVATED, String.valueOf(event.getUserId()), event);
    }
}
