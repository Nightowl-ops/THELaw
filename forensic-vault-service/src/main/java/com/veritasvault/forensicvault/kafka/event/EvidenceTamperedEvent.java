package com.veritasvault.forensicvault.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceTamperedEvent {
    private Long evidenceId;
    private String expectedHash;
    private String actualHash;
    private Long examinerId;
    private String reason;
    private LocalDateTime timestamp;
}
