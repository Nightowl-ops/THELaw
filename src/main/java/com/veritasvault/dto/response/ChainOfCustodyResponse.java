package com.veritasvault.dto.response;

import com.veritasvault.model.enums.CustodyActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainOfCustodyResponse {

    private Long id;
    private Long evidenceId;
    private String evidenceTrackingCode;
    private String evidenceName;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private CustodyActionType actionType;
    private String notes;
    private String hashAtEvent;
    private LocalDateTime loggedAt;
}