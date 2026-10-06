package com.veritasvault.controller;

import com.veritasvault.dto.response.ChainOfCustodyResponse;
import com.veritasvault.service.ChainOfCustodyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/custody/logs")
@RequiredArgsConstructor
public class ChainOfCustodyController {

    private final ChainOfCustodyService custodyService;

    /**
     * Retrieves the immutable audit ledger for a specific evidence item.
     * Accessible by Attorneys, Forensic Examiners, and Admins.
     */
    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<ChainOfCustodyResponse>> getAuditTrail(@PathVariable Long evidenceId) {
        List<ChainOfCustodyResponse> trail = custodyService.getAuditTrailForEvidence(evidenceId);
        return ResponseEntity.ok(trail);
    }
}