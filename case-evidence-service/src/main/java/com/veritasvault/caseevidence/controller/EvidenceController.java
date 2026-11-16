package com.veritasvault.caseevidence.controller;

import com.veritasvault.caseevidence.dto.request.EvidenceUploadRequest;
import com.veritasvault.caseevidence.dto.response.EvidenceResponse;
import com.veritasvault.caseevidence.model.enums.EvidenceStatus;
import com.veritasvault.caseevidence.model.enums.EvidenceType;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.service.EvidenceService;
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
public class EvidenceController {

    private final EvidenceService evidenceService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") EvidenceUploadRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        EvidenceResponse response = evidenceService.uploadEvidence(file, request, userPrincipal);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceById(@PathVariable Long id) {
        EvidenceResponse response = evidenceService.getEvidenceById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tracking/{trackingCode}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> getEvidenceByTrackingCode(@PathVariable String trackingCode) {
        EvidenceResponse response = evidenceService.getEvidenceByTrackingCode(trackingCode);
        return ResponseEntity.ok(response);
    }

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

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<EvidenceResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") EvidenceStatus newStatus,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        EvidenceResponse response = evidenceService.updateStatus(id, newStatus, notes, userPrincipal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/simulate-tamper")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<String> simulateTamper(@PathVariable Long id) {
        evidenceService.simulateTampering(id);
        return ResponseEntity.ok("Tampering simulated: physical file byte modified on disk.");
    }
}
