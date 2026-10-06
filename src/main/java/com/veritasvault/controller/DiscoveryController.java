package com.veritasvault.controller;

import com.veritasvault.dto.request.CreateDiscoveryRequest;
import com.veritasvault.dto.response.DiscoveryResponse;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.DiscoveryProductionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryProductionService discoveryService;

    /**
     * Creates a discovery production bundle from an exhibit binder and generates an expiring access token.
     * Restricted to Attorneys and Admins.
     */
    @PostMapping("/produce/{binderId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<DiscoveryResponse> createProduction(
            @PathVariable Long binderId,
            @Valid @RequestBody CreateDiscoveryRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        DiscoveryResponse response = discoveryService.createProduction(request, binderId, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Public endpoint for external counsel to access the discovery package via secure token.
     * Validates expiration date and logs client IP & User-Agent for Proof of Service.
     */
    @GetMapping("/access/{accessToken}")
    public ResponseEntity<DiscoveryResponse> accessDiscoveryPackage(
            @PathVariable String accessToken,
            HttpServletRequest request
    ) {
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DiscoveryResponse response = discoveryService.accessProductionByToken(accessToken, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves discovery metadata by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<DiscoveryResponse> getProductionById(@PathVariable Long id) {
        DiscoveryResponse response = discoveryService.getProductionById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all discovery packages generated for a specific case.
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<DiscoveryResponse>> getProductionsByCaseId(@PathVariable Long caseId) {
        List<DiscoveryResponse> productions = discoveryService.getProductionsByCaseId(caseId);
        return ResponseEntity.ok(productions);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}