package com.veritasvault.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
