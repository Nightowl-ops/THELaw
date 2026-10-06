package com.veritasvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper; // Spring Boot automatically provides Jackson ObjectMapper

    public SseEmitter subscribe() {
        // 30-minute timeout for real-time telemetry stream
        SseEmitter emitter = new SseEmitter(1800000L);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Subscribed to VeritasVault Real-Time Telemetry Stream"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(String eventName, Object payload) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        // Convert the payload DTO into a valid JSON string before sending
        String jsonPayload;
        try {
            jsonPayload = (payload instanceof String) ? (String) payload : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload for event: {}", eventName, e);
            jsonPayload = "{}";
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(jsonPayload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}