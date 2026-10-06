package com.veritasvault.dto.response;

import com.veritasvault.model.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyReservationResponse {

    private Long id;
    private Long evidenceItemId;
    private String evidenceTrackingCode;
    private String evidenceName;
    private Long examinerId;
    private String examinerName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}