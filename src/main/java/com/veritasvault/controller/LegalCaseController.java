package com.veritasvault.controller;

import com.veritasvault.dto.request.CreateCaseRequest;
import com.veritasvault.dto.response.CaseResponse;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.LegalCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    /**
     * Creates a new legal case.
     * Restricted to Attorneys and Admins.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        CaseResponse response = legalCaseService.createCase(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves all legal cases.
     * Restricted to Attorneys, Forensic Examiners, and Admins.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<CaseResponse>> getAllCases() {
        List<CaseResponse> cases = legalCaseService.getAllCases();
        return ResponseEntity.ok(cases);
    }

    /*
     * Retrieves a single case by its primary database ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(@PathVariable Long id) {
        CaseResponse response = legalCaseService.getCaseById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all cases assigned to a specific attorney.
     */
    @GetMapping("/attorney/{attorneyId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<CaseResponse>> getCasesForAttorney(@PathVariable Long attorneyId) {
        List<CaseResponse> cases = legalCaseService.getCasesForAttorney(attorneyId);
        return ResponseEntity.ok(cases);
    }
}