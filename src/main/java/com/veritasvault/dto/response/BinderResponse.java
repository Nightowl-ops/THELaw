package com.veritasvault.dto.response;

import com.veritasvault.model.enums.BinderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinderResponse {

    private Long id;
    private Long caseId;
    private String caseNumber;
    private String name;
    private String batesPrefix;
    private BinderStatus status;
    private Integer totalExhibits;
    private Integer totalPages;
    private Boolean isLocked;
    private Long createdById;
    private String createdByName;
    private List<ExhibitItemResponse> exhibits;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExhibitItemResponse {
        private Long id;
        private Long evidenceId;
        private String evidenceTrackingCode;
        private String evidenceName;
        private String exhibitLabel;
        private Integer pageCount;
        private String batesStart;
        private String batesEnd;
    }
}