package com.veritasvault.forensicvault.controller;

import com.veritasvault.forensicvault.dto.response.ChainOfCustodyResponse;
import com.veritasvault.forensicvault.service.ChainOfCustodyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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

    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<ChainOfCustodyResponse>> getAuditTrail(
            @PathVariable Long evidenceId,
            HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        List<ChainOfCustodyResponse> trail = custodyService.getAuditTrailForEvidence(evidenceId, jwt);
        return ResponseEntity.ok(trail);
    }

    private String extractJwt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header)) {
            return header;
        }
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return "Bearer " + token;
        }
        return null;
    }
}
