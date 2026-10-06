package com.veritasvault.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound payload validating exhibit inclusion within a trial binder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddExhibitRequest {

    @NotNull(message = "Evidence item ID is required")
    private Long evidenceId;

    @NotBlank(message = "Exhibit label is required (e.g., 'Exhibit A', 'Exhibit 102')")
    @Size(min = 1, max = 50, message = "Exhibit label cannot exceed 50 characters")
    private String exhibitLabel;

    @NotNull(message = "Page count is required for Bates numbering calculation")
    @Min(value = 1, message = "Page count must be at least 1")
    private Integer pageCount;

    @Min(value = 1, message = "Display sequence order must be 1 or greater")
    private Integer sequenceOrder;

    private String notes;
}