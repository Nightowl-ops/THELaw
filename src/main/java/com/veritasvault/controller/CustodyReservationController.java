package com.veritasvault.controller;

import com.veritasvault.dto.request.CreateLabReportRequest;
import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CaseAuditResponse;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.dto.response.ForensicLabReportResponse;
import com.veritasvault.model.enums.CustodyActionType;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.CustodyReservationService;
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

@RestController
@RequestMapping("/api/custody/reservations")
@RequiredArgsConstructor
@Tag(name = "Custody Reservations", description = "Endpoints for scheduling forensic lab time slots, checkout, and checkin workflows")
public class CustodyReservationController {

    private final CustodyReservationService reservationService;

    @Operation(summary = "Book forensic lab custody time slot",
               description = "Reserves a lab slot for evidence analysis. Employs pessimistic row locking and interval overlap collision checks.")
    @ApiResponse(responseCode = "201", description = "Reservation successfully booked")
    @ApiResponse(responseCode = "400", description = "Invalid time boundaries or sequestered evidence")
    @ApiResponse(responseCode = "409", description = "Double-booking time conflict detected")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> createReservation(
            @Valid @RequestBody CustodyReservationRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long examinerId = userDetails.getUser().getId();
        CustodyReservationResponse response = reservationService.createReservation(request, examinerId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Check out evidence for analysis",
               description = "Marks evidence as IN_LAB and transitions reservation to ACTIVE. Strictly enforces examiner ownership.")
    @ApiResponse(responseCode = "200", description = "Evidence checked out successfully")
    @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned examiner can check out")
    @PatchMapping("/{id}/checkout")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> checkOutEvidence(
            @PathVariable Long id,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        CustodyReservationResponse response = reservationService.checkOutEvidence(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check in evidence from analysis",
               description = "Returns evidence to AVAILABLE and marks reservation COMPLETED. Strictly enforces examiner ownership.")
    @ApiResponse(responseCode = "200", description = "Evidence checked in successfully")
    @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned examiner can check in")
    @PatchMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> checkInEvidence(
            @PathVariable Long id,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "actionType", required = false) CustodyActionType actionType,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        CustodyReservationResponse response = reservationService.checkInEvidence(id, notes, actionType, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check in evidence with multithreaded forensic lab report",
               description = "Executes parallel cryptographic hash verification via ExecutorService, generates ForensicLabReport, and completes reservation.")
    @ApiResponse(responseCode = "200", description = "Evidence checked in and forensic lab report generated")
    @ApiResponse(responseCode = "400", description = "Invalid reservation state")
    @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned examiner can check in")
    @PostMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<ForensicLabReportResponse> checkInWithLabReport(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateLabReportRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        ForensicLabReportResponse response = reservationService.checkInWithLabReport(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get forensic lab report for a reservation",
               description = "Retrieves the verification telemetry, hash comparisons, and findings for a completed custody reservation")
    @ApiResponse(responseCode = "200", description = "Forensic lab report returned")
    @ApiResponse(responseCode = "404", description = "Report not found")
    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<ForensicLabReportResponse> getLabReportByReservationId(@PathVariable Long id) {
        ForensicLabReportResponse response = reservationService.getLabReportByReservationId(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Batch case evidence integrity audit",
               description = "Concurrently recalculates cryptographic SHA-256 hashes across all evidence in a case using invokeAll() on the 4-worker thread pool.")
    @ApiResponse(responseCode = "200", description = "Batch audit completed with report")
    @ApiResponse(responseCode = "403", description = "Forbidden - Restricted to attorneys, examiners, or admins")
    @PostMapping("/case/{caseId}/audit")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CaseAuditResponse> auditCaseEvidenceIntegrity(
            @PathVariable Long caseId,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        CaseAuditResponse response = reservationService.auditCaseEvidenceIntegrity(caseId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel custody reservation",
               description = "Cancels a pending or confirmed reservation and releases the slot. Restricted to assigned examiner or admin.")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled")
    @ApiResponse(responseCode = "403", description = "Forbidden - Only assigned examiner or admin can cancel")
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> cancelReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        CustodyReservationResponse response = reservationService.cancelReservation(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get reservation by ID", description = "Retrieves reservation metadata by ID")
    @ApiResponse(responseCode = "200", description = "Reservation returned")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> getReservationById(@PathVariable Long id) {
        CustodyReservationResponse response = reservationService.getReservationById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List reservations for an evidence item",
               description = "Retrieves paginated bookings for a specific evidence item")
    @ApiResponse(responseCode = "200", description = "Page of reservations returned")
    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CustodyReservationResponse>> getReservationsForEvidence(
            @PathVariable Long evidenceId,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable
    ) {
        Page<CustodyReservationResponse> reservations = reservationService.getReservationsForEvidence(evidenceId, pageable);
        return ResponseEntity.ok(reservations);
    }

    @Operation(summary = "List reservations by examiner",
               description = "Retrieves paginated bookings scheduled by an examiner")
    @ApiResponse(responseCode = "200", description = "Page of reservations returned")
    @GetMapping("/examiner/{examinerId}")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<CustodyReservationResponse>> getReservationsForExaminer(
            @PathVariable Long examinerId,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable
    ) {
        Page<CustodyReservationResponse> reservations = reservationService.getReservationsForExaminer(examinerId, pageable);
        return ResponseEntity.ok(reservations);
    }
}