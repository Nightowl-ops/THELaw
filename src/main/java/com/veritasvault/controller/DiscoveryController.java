package com.veritasvault.controller;

import com.veritasvault.dto.request.CreateDiscoveryRequest;
import com.veritasvault.dto.response.DiscoveryReceiptResponse;
import com.veritasvault.dto.response.DiscoveryResponse;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.DiscoveryProductionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Discovery Production", description = "Endpoints for creating secure discovery packages, token verification, and Proof of Service logging")
public class DiscoveryController {

    private final DiscoveryProductionService discoveryService;

    @Operation(summary = "Produce discovery package from binder",
               description = "Locks the binder as SERVED and generates a hashed, time-expiring external token link")
    @ApiResponse(responseCode = "201", description = "Discovery package produced")
    @ApiResponse(responseCode = "400", description = "Expiration date not in future or binder mismatch")
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

    @Operation(summary = "Access discovery package via token",
               description = "Public endpoint for external counsel to inspect metadata and verify validity. Records access IP as Proof of Service.")
    @ApiResponse(responseCode = "200", description = "Package details returned")
    @ApiResponse(responseCode = "400", description = "Link has expired")
    @ApiResponse(responseCode = "404", description = "Invalid token")
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

    @Operation(summary = "Download discovery production package",
               description = "Public streaming endpoint for external counsel to download evidence bundle using the secure token")
    @ApiResponse(responseCode = "200", description = "Binary exhibit bundle stream")
    @ApiResponse(responseCode = "400", description = "Link has expired")
    @ApiResponse(responseCode = "404", description = "Invalid token or evidence file missing")
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

    @Operation(summary = "Get delivery and access receipt logs (Proof of Service)",
               description = "Retrieves all external IP and timestamp logs recording opposing counsel access attempts")
    @ApiResponse(responseCode = "200", description = "Access receipt logs returned")
    @ApiResponse(responseCode = "404", description = "Production package not found")
    @GetMapping("/{productionId}/receipts")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<DiscoveryReceiptResponse>> getReceipts(@PathVariable Long productionId) {
        List<DiscoveryReceiptResponse> receipts = discoveryService.getReceipts(productionId);
        return ResponseEntity.ok(receipts);
    }

    @Operation(summary = "Get discovery production by ID", description = "Retrieves discovery metadata by internal ID")
    @ApiResponse(responseCode = "200", description = "Discovery package returned")
    @ApiResponse(responseCode = "404", description = "Not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<DiscoveryResponse> getProductionById(@PathVariable Long id) {
        DiscoveryResponse response = discoveryService.getProductionById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List discovery packages for a case",
               description = "Retrieves all discovery packages produced under a legal case docket")
    @ApiResponse(responseCode = "200", description = "List of discovery packages returned")
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<List<DiscoveryResponse>> getProductionsByCaseId(@PathVariable Long caseId) {
        List<DiscoveryResponse> productions = discoveryService.getProductionsByCaseId(caseId);
        return ResponseEntity.ok(productions);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}