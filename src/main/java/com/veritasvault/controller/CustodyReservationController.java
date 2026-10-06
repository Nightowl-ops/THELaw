package com.veritasvault.controller;

import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.CustodyReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/custody/reservations")
@RequiredArgsConstructor
public class CustodyReservationController {

    private final CustodyReservationService reservationService;

    /**
     * Books a forensic lab time slot for an evidence item.
     * Enforces double-booking conflict detection.
     */
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

    /**
     * Checks out evidence for active lab analysis.
     * Transitions reservation to ACTIVE, evidence to IN_LAB, and writes CHECK_OUT audit log.
     */
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

    /**
     * Returns evidence from lab analysis.
     * Transitions reservation to COMPLETED, evidence to AVAILABLE, and writes CHECK_IN audit log.
     */
    @PatchMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> checkInEvidence(
            @PathVariable Long id,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        CustodyReservationResponse response = reservationService.checkInEvidence(id, notes, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a pending/confirmed reservation and releases the slot.
     */
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

    /**
     * Retrieves reservation details by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<CustodyReservationResponse> getReservationById(@PathVariable Long id) {
        CustodyReservationResponse response = reservationService.getReservationById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all lab reservations tied to a specific evidence item.
     */
    @GetMapping("/evidence/{evidenceId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<CustodyReservationResponse>> getReservationsForEvidence(@PathVariable Long evidenceId) {
        List<CustodyReservationResponse> reservations = reservationService.getReservationsForEvidence(evidenceId);
        return ResponseEntity.ok(reservations);
    }

    /**
     * Retrieves all lab reservations scheduled by a specific examiner.
     */
    @GetMapping("/examiner/{examinerId}")
    @PreAuthorize("hasAnyRole('ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<CustodyReservationResponse>> getReservationsForExaminer(@PathVariable Long examinerId) {
        List<CustodyReservationResponse> reservations = reservationService.getReservationsForExaminer(examinerId);
        return ResponseEntity.ok(reservations);
    }
}