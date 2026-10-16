package com.veritasvault.controller;

import com.veritasvault.dto.response.ChainOfCustodyResponse;
import com.veritasvault.service.ChainOfCustodyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing append-only, tamper-evident Chain of Custody audit ledgers.
 */
@RestController
@RequestMapping("/api/custody/logs")
@RequiredArgsConstructor
@Tag(name = "Chain of Custody Ledger", description = "Endpoints for inspecting chronological custodial audit logs and cryptographic hash histories")
public class ChainOfCustodyController {

    private final ChainOfCustodyService custodyService;

    @Operation(summary = "Get complete chain of custody ledger for evidence",
               description = "Returns chronological, tamper-evident audit log of all events, actors, and SHA-256 states")
    @ApiResponse(responseCode = "200", description = "Audit ledger returned")
    @ApiResponse(responseCode = "404", description = "Evidence item not found")
    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<ChainOfCustodyResponse>> getAuditTrail(@PathVariable Long evidenceId) {
        List<ChainOfCustodyResponse> trail = custodyService.getAuditTrailForEvidence(evidenceId);
        return ResponseEntity.ok(trail);
    }
}