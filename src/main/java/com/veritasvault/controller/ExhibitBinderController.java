package com.veritasvault.controller;

import com.veritasvault.dto.request.AddExhibitRequest;
import com.veritasvault.dto.request.CreateBinderRequest;
import com.veritasvault.dto.response.BinderResponse;
import com.veritasvault.model.enums.BinderStatus;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.ExhibitBinderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/binders")
@RequiredArgsConstructor
public class ExhibitBinderController {

    private final ExhibitBinderService binderService;

    /**
     * Initializes a new exhibit binder for a case.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> createBinder(
            @Valid @RequestBody CreateBinderRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        Long currentUserId = userDetails.getUser().getId();
        BinderResponse response = binderService.createBinder(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Attaches an evidence item to a binder and assigns consecutive Bates numbers.
     * Fails with 400 Bad Request if binder is FINALIZED or SERVED.
     */
    @PostMapping("/{binderId}/exhibits")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> addExhibitToBinder(
            @PathVariable Long binderId,
            @Valid @RequestBody AddExhibitRequest request
    ) {
        BinderResponse response = binderService.addExhibitToBinder(binderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates binder lifecycle status (DRAFT, UNDER_REVIEW, FINALIZED, SERVED).
     */
    @PatchMapping("/{binderId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> updateStatus(
            @PathVariable Long binderId,
            @RequestParam("status") BinderStatus status
    ) {
        BinderResponse response = binderService.updateStatus(binderId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an exhibit binder and its nested exhibits with calculated Bates ranges.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> getBinderById(@PathVariable Long id) {
        BinderResponse response = binderService.getBinderById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all exhibit binders for a case.
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<List<BinderResponse>> getBindersByCaseId(@PathVariable Long caseId) {
        List<BinderResponse> binders = binderService.getBindersByCaseId(caseId);
        return ResponseEntity.ok(binders);
    }
}