package com.veritasvault.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBinderRequest {

    @NotNull(message = "Case ID is required")
    private Long caseId;

    @NotBlank(message = "Binder name is required")
    @Size(min = 3, max = 150, message = "Binder name must be between 3 and 150 characters")
    private String name;

    private String description;

    @NotBlank(message = "Bates prefix is required")
    @Pattern(
            regexp = "^[A-Z0-9_-]{2,12}$",
            message = "Bates prefix must be 2 to 12 uppercase alphanumeric characters, dashes, or underscores (e.g., 'PLTF', 'DEF-01')"
    )
    private String batesPrefix;

    @Size(max = 100, message = "Court name cannot exceed 100 characters")
    private String courtName;

    @Size(max = 100, message = "Judge name cannot exceed 100 characters")
    private String presidingJudge;
}