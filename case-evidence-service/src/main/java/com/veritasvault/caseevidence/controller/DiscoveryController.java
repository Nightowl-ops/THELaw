package com.veritasvault.caseevidence.controller;

import com.veritasvault.caseevidence.dto.request.CreateDiscoveryRequest;
import com.veritasvault.caseevidence.dto.response.DiscoveryReceiptResponse;
import com.veritasvault.caseevidence.dto.response.DiscoveryResponse;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.service.DiscoveryProductionService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryProductionService discoveryService;

    @PostMapping("/produce/{binderId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<DiscoveryResponse> createProduction(
            @PathVariable Long binderId,
            @Valid @RequestBody CreateDiscoveryRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        DiscoveryResponse response = discoveryService.createProduction(request, binderId, userPrincipal);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

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

    @GetMapping("/download/{accessToken}")
    public ResponseEntity<Resource> downloadDiscoveryPackage(
            @PathVariable String accessToken,
            HttpServletRequest request
    ) {
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DiscoveryProductionService.DownloadBundle bundle = discoveryService.loadProductionBundle(accessToken, clientIp, userAgent);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.fileName() + "\"")
                .body(bundle.resource());
    }

    @GetMapping("/{productionId}/receipts")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<DiscoveryReceiptResponse>> getReceipts(@PathVariable Long productionId) {
        List<DiscoveryReceiptResponse> receipts = discoveryService.getReceipts(productionId);
        return ResponseEntity.ok(receipts);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<DiscoveryResponse> getProductionById(@PathVariable Long id) {
        DiscoveryResponse response = discoveryService.getProductionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<DiscoveryResponse>> getProductionsByCaseId(@PathVariable Long caseId) {
        List<DiscoveryResponse> responses = discoveryService.getProductionsByCaseId(caseId);
        return ResponseEntity.ok(responses);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
