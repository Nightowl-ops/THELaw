package com.veritasvault.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit receipt confirming external discovery document access and legal Proof of Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryReceiptResponse {

    private Long id;
    private Long productionId;
    private String ipAddress;
    private String userAgent;
    private String downloadedFileName;
    private LocalDateTime accessedAt;
}
