package com.veritasvault.forensicvault.controller;

import com.veritasvault.forensicvault.dto.request.CreateLabReportRequest;
import com.veritasvault.forensicvault.dto.request.CustodyReservationRequest;
import com.veritasvault.forensicvault.dto.response.CaseAuditResponse;
import com.veritasvault.forensicvault.dto.response.CustodyReservationResponse;
import com.veritasvault.forensicvault.dto.response.ForensicLabReportResponse;
import com.veritasvault.forensicvault.model.enums.CustodyActionType;
import com.veritasvault.forensicvault.security.UserPrincipal;
import com.veritasvault.forensicvault.service.CustodyReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/custody/reservations")
@RequiredArgsConstructor
public class CustodyReservationController {

    private final CustodyReservationService reservationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> createReservation(
            @Valid @RequestBody CustodyReservationRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long examinerId = userPrincipal.getUserId();
        String jwt = extractJwt(httpRequest);
        CustodyReservationResponse response = reservationService.createReservation(request, examinerId, jwt);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/checkout")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> checkOutEvidence(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = userPrincipal.getUserId();
        String userRole = userPrincipal.getRole();
        String jwt = extractJwt(httpRequest);
        CustodyReservationResponse response = reservationService.checkOutEvidence(id, currentUserId, userRole, jwt);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> checkInEvidence(
            @PathVariable Long id,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "actionType", required = false) CustodyActionType actionType,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = userPrincipal.getUserId();
        String userRole = userPrincipal.getRole();
        String jwt = extractJwt(httpRequest);
        CustodyReservationResponse response = reservationService.checkInEvidence(id, notes, actionType, currentUserId, userRole, jwt);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<ForensicLabReportResponse> checkInWithLabReport(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateLabReportRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = userPrincipal.getUserId();
        String userRole = userPrincipal.getRole();
        String jwt = extractJwt(httpRequest);
        ForensicLabReportResponse response = reservationService.checkInWithLabReport(id, request, currentUserId, userRole, jwt);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<ForensicLabReportResponse> getLabReportByReservationId(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        ForensicLabReportResponse response = reservationService.getLabReportByReservationId(id, jwt);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/case/{caseId}/audit")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CaseAuditResponse> auditCaseEvidenceIntegrity(
            @PathVariable Long caseId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = userPrincipal.getUserId();
        String userRole = userPrincipal.getRole();
        String jwt = extractJwt(httpRequest);
        CaseAuditResponse response = reservationService.auditCaseEvidenceIntegrity(caseId, currentUserId, userRole, jwt);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> cancelReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = userPrincipal.getUserId();
        String userRole = userPrincipal.getRole();
        String jwt = extractJwt(httpRequest);
        CustodyReservationResponse response = reservationService.cancelReservation(id, currentUserId, userRole, jwt);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> getReservationById(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        CustodyReservationResponse response = reservationService.getReservationById(id, jwt);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CustodyReservationResponse>> getReservationsForEvidence(
            @PathVariable Long evidenceId,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable,
            HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        Page<CustodyReservationResponse> reservations = reservationService.getReservationsForEvidence(evidenceId, pageable, jwt);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/examiner/{examinerId}")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CustodyReservationResponse>> getReservationsForExaminer(
            @PathVariable Long examinerId,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable,
            HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        Page<CustodyReservationResponse> reservations = reservationService.getReservationsForExaminer(examinerId, pageable, jwt);
        return ResponseEntity.ok(reservations);
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
