package com.veritasvault.auth.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeactivatedEvent {
    private Long userId;
    private String email;
    private String reason;
    private LocalDateTime timestamp;
}
