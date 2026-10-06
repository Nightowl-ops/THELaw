package com.veritasvault.controller;

import com.veritasvault.dto.request.EvidenceUploadRequest;
import com.veritasvault.dto.response.EvidenceResponse;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.EvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;

    /**
     * Uploads a new evidence item, computes SHA-256 fingerprint, saves to disk,
     * and writes an initial INGEST custody entry.
     * Consumes multipart/form-data.
     */
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

    /**
     * Retrieves evidence details by internal database ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceById(@PathVariable Long id) {
        EvidenceResponse response = evidenceService.getEvidenceById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves evidence details by tracking code (e.g., 'EV-2026-0001').
     */
    @GetMapping("/tracking/{trackingCode}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceByTrackingCode(@PathVariable String trackingCode) {
        EvidenceResponse response = evidenceService.getEvidenceByTrackingCode(trackingCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all evidence items tied to a specific case.
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<EvidenceResponse>> getEvidenceByCaseId(@PathVariable Long caseId) {
        List<EvidenceResponse> items = evidenceService.getEvidenceByCaseId(caseId);
        return ResponseEntity.ok(items);
    }

    /**
     * Streams the stored evidence file for download.
     */
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

    /**
     * Updates evidence operational status (e.g., AVAILABLE, SEQUESTERED)
     * and appends a corresponding immutable Chain of Custody entry.
     */
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