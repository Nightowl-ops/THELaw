package com.veritasvault.forensicvault.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLabReportRequest {

    @Size(max = 2000, message = "Findings cannot exceed 2000 characters")
    private String examinerFindings;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}
