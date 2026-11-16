package com.veritasvault.caseevidence.controller;

import com.veritasvault.caseevidence.dto.request.AddExhibitRequest;
import com.veritasvault.caseevidence.dto.request.CreateBinderRequest;
import com.veritasvault.caseevidence.dto.response.BinderResponse;
import com.veritasvault.caseevidence.model.enums.BinderStatus;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.service.ExhibitBinderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/binders")
@RequiredArgsConstructor
public class ExhibitBinderController {

    private final ExhibitBinderService binderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> createBinder(
            @Valid @RequestBody CreateBinderRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        BinderResponse response = binderService.createBinder(request, userPrincipal);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{binderId}/exhibits")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> addExhibitToBinder(
            @PathVariable Long binderId,
            @Valid @RequestBody AddExhibitRequest request
    ) {
        BinderResponse response = binderService.addExhibitToBinder(binderId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{binderId}/exhibits/{exhibitId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> removeExhibitFromBinder(
            @PathVariable Long binderId,
            @PathVariable Long exhibitId
    ) {
        BinderResponse response = binderService.removeExhibitFromBinder(binderId, exhibitId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{binderId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> updateStatus(
            @PathVariable Long binderId,
            @RequestParam("status") BinderStatus status
    ) {
        BinderResponse response = binderService.updateStatus(binderId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> getBinderById(@PathVariable Long id) {
        BinderResponse response = binderService.getBinderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<BinderResponse>> getBindersByCaseId(
            @PathVariable Long caseId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<BinderResponse> binders = binderService.getBindersByCaseId(caseId, pageable);
        return ResponseEntity.ok(binders);
    }
}
