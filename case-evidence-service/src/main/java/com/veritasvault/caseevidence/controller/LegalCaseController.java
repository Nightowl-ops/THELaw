package com.veritasvault.caseevidence.controller;

import com.veritasvault.caseevidence.dto.request.CreateCaseRequest;
import com.veritasvault.caseevidence.dto.response.CaseResponse;
import com.veritasvault.caseevidence.model.enums.CaseStatus;
import com.veritasvault.caseevidence.model.enums.CaseType;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.service.LegalCaseService;
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

import java.util.List;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class LegalCaseController {

    private final LegalCaseService legalCaseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CaseResponse response = legalCaseService.createCase(request, userPrincipal);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CaseResponse>> getCases(
            @RequestParam(value = "status", required = false) CaseStatus status,
            @RequestParam(value = "type", required = false) CaseType type,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Page<CaseResponse> cases = legalCaseService.getCases(status, type, search, userPrincipal, pageable);
        return ResponseEntity.ok(cases);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CaseResponse response = legalCaseService.getCaseById(id, userPrincipal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> updateCaseStatus(
            @PathVariable Long id,
            @RequestParam("status") CaseStatus status
    ) {
        CaseResponse response = legalCaseService.updateCaseStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attorney/{attorneyId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<CaseResponse>> getCasesForAttorney(@PathVariable Long attorneyId) {
        List<CaseResponse> cases = legalCaseService.getCasesForAttorney(attorneyId);
        return ResponseEntity.ok(cases);
    }
}
