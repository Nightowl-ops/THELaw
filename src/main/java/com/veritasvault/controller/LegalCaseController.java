package com.veritasvault.controller;

import com.veritasvault.dto.request.CreateCaseRequest;
import com.veritasvault.dto.response.CaseResponse;
import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.LegalCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * Controller exposing legal case docket endpoints, attorney assignments,
 * and status transitions.
 */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Tag(name = "Legal Cases", description = "Endpoints for managing legal cases, docket numbers, assignments, and case status")
public class LegalCaseController {

    private final LegalCaseService legalCaseService;

    @Operation(summary = "Create a new legal case",
               description = "Registers an official court case docket. Restricted to Attorneys and Admins.")
    @ApiResponse(responseCode = "201", description = "Case successfully created")
    @ApiResponse(responseCode = "400", description = "Validation error or invalid attorney/client reference")
    @ApiResponse(responseCode = "409", description = "Duplicate case number conflict")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        CaseResponse response = legalCaseService.createCase(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "List legal cases with pagination & filtering",
               description = "Returns paginated legal cases. Clients automatically see only their own cases; firm staff can view all.")
    @ApiResponse(responseCode = "200", description = "Page of cases returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CaseResponse>> getCases(
            @RequestParam(value = "status", required = false) CaseStatus status,
            @RequestParam(value = "type", required = false) CaseType type,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Page<CaseResponse> cases = legalCaseService.getCases(status, type, search, userDetails.getUser(), pageable);
        return ResponseEntity.ok(cases);
    }

    @Operation(summary = "Get legal case by ID",
               description = "Retrieves case details. Strictly enforces client ownership.")
    @ApiResponse(responseCode = "200", description = "Case dossier returned")
    @ApiResponse(responseCode = "403", description = "Forbidden - Client cannot view another party's case")
    @ApiResponse(responseCode = "404", description = "Case not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable Long id,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        CaseResponse response = legalCaseService.getCaseById(id, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update case status",
               description = "Transitions case lifecycle (ACTIVE, PENDING_TRIAL, SETTLED, ARCHIVED).")
    @ApiResponse(responseCode = "200", description = "Case status updated")
    @ApiResponse(responseCode = "404", description = "Case not found")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<CaseResponse> updateCaseStatus(
            @PathVariable Long id,
            @RequestParam("status") CaseStatus status
    ) {
        CaseResponse response = legalCaseService.updateCaseStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get cases assigned to attorney",
               description = "Retrieves all legal cases managed by a specific attorney ID.")
    @ApiResponse(responseCode = "200", description = "List of assigned cases returned")
    @GetMapping("/attorney/{attorneyId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<CaseResponse>> getCasesForAttorney(@PathVariable Long attorneyId) {
        List<CaseResponse> cases = legalCaseService.getCasesForAttorney(attorneyId);
        return ResponseEntity.ok(cases);
    }
}