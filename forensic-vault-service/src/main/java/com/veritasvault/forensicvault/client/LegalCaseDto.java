package com.veritasvault.forensicvault.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalCaseDto {
    private Long id;
    private String caseNumber;
    private String caseTitle;
    private String status;
}
