package com.veritasvault.controller;

import com.veritasvault.dto.request.EvidenceUploadRequest;
import com.veritasvault.dto.response.EvidenceResponse;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
@Tag(name = "Evidence Vault", description = "Endpoints for evidence ingestion, streaming SHA-256 fingerprinting, retrieval, and status updates")
public class EvidenceController {

    private final EvidenceService evidenceService;

    @Operation(summary = "Upload and ingest digital evidence item",
               description = "Computes SHA-256 fingerprint via streaming, stores file safely, and logs initial INGEST entry")
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(
                            name = "data",
                            contentType = MediaType.APPLICATION_JSON_VALUE
                    )
            )
    )
    @ApiResponse(responseCode = "201", description = "Evidence uploaded and ingested with SHA-256 fingerprint")
    @ApiResponse(responseCode = "400", description = "Invalid payload or empty file")
    @ApiResponse(responseCode = "413", description = "File exceeds maximum upload size")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") EvidenceUploadRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        EvidenceResponse response = evidenceService.uploadEvidence(file, request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Get evidence by ID", description = "Retrieves evidence metadata by internal database ID")
    @ApiResponse(responseCode = "200", description = "Evidence metadata returned")
    @ApiResponse(responseCode = "404", description = "Evidence item not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceById(@PathVariable Long id) {
        EvidenceResponse response = evidenceService.getEvidenceById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get evidence by tracking code", description = "Retrieves evidence by unique court tracking code (e.g., 'EV-2026-0001')")
    @ApiResponse(responseCode = "200", description = "Evidence metadata returned")
    @ApiResponse(responseCode = "404", description = "Tracking code not found")
    @GetMapping("/tracking/{trackingCode}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceByTrackingCode(@PathVariable String trackingCode) {
        EvidenceResponse response = evidenceService.getEvidenceByTrackingCode(trackingCode);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List evidence items in case with pagination & filtering",
               description = "Retrieves paginated evidence items tied to a legal case with optional status and type filters")
    @ApiResponse(responseCode = "200", description = "Page of evidence items returned")
    @ApiResponse(responseCode = "404", description = "Legal case not found")
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<EvidenceResponse>> getEvidenceByCaseId(
            @PathVariable Long caseId,
            @RequestParam(value = "status", required = false) EvidenceStatus status,
            @RequestParam(value = "type", required = false) EvidenceType type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<EvidenceResponse> items = evidenceService.getEvidenceByCaseId(caseId, status, type, pageable);
        return ResponseEntity.ok(items);
    }

    @Operation(summary = "Download evidence binary file", description = "Streams the stored evidence binary file from disk")
    @ApiResponse(responseCode = "200", description = "Evidence file stream")
    @ApiResponse(responseCode = "404", description = "Evidence or file not found")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Resource> downloadEvidenceFile(@PathVariable Long id) {
        Resource resource = evidenceService.loadEvidenceFile(id);
        EvidenceResponse evidence = evidenceService.getEvidenceById(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + evidence.getName() + "\"")
                .body(resource);
    }

    @Operation(summary = "Update evidence operational status",
               description = "Enforces state machine transition rules and logs audit entry in Chain of Custody")
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @ApiResponse(responseCode = "400", description = "Illegal state transition attempted")
    @ApiResponse(responseCode = "404", description = "Evidence not found")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") EvidenceStatus newStatus,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        EvidenceResponse response = evidenceService.updateStatus(id, newStatus, notes, currentUserId);
        return ResponseEntity.ok(response);
    }
}