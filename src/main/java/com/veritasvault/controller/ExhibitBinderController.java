package com.veritasvault.controller;

import com.veritasvault.dto.request.AddExhibitRequest;
import com.veritasvault.dto.request.CreateBinderRequest;
import com.veritasvault.dto.response.BinderResponse;
import com.veritasvault.model.enums.BinderStatus;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.ExhibitBinderService;
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

/**
 * Controller managing trial exhibit binders, Bates stamping, and immutability locks.
 */
@RestController
@RequestMapping("/api/binders")
@RequiredArgsConstructor
@Tag(name = "Exhibit Binders", description = "Endpoints for assembling trial binders, Bates numbering, and exhibit locking")
public class ExhibitBinderController {

    private final ExhibitBinderService binderService;

    @Operation(summary = "Create an exhibit binder",
               description = "Initializes an official trial binder in DRAFT status with a customized Bates prefix")
    @ApiResponse(responseCode = "201", description = "Binder created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid payload or Bates prefix format")
    @ApiResponse(responseCode = "409", description = "Duplicate binder name in this case")
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

    @Operation(summary = "Attach evidence exhibit to binder",
               description = "Appends evidence to a mutable binder and calculates consecutive Bates ranges")
    @ApiResponse(responseCode = "200", description = "Exhibit attached; Bates ranges calculated")
    @ApiResponse(responseCode = "400", description = "Binder is locked (FINALIZED or SERVED) or evidence belongs to another case")
    @ApiResponse(responseCode = "409", description = "Evidence is already in this binder")
    @PostMapping("/{binderId}/exhibits")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> addExhibitToBinder(
            @PathVariable Long binderId,
            @Valid @RequestBody AddExhibitRequest request
    ) {
        BinderResponse response = binderService.addExhibitToBinder(binderId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove exhibit from binder",
               description = "Removes an exhibit from a mutable binder and automatically recalculates consecutive Bates offsets")
    @ApiResponse(responseCode = "200", description = "Exhibit removed and Bates sequence updated")
    @ApiResponse(responseCode = "400", description = "Binder is locked (FINALIZED or SERVED)")
    @ApiResponse(responseCode = "404", description = "Exhibit or binder not found")
    @DeleteMapping("/{binderId}/exhibits/{exhibitId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> removeExhibitFromBinder(
            @PathVariable Long binderId,
            @PathVariable Long exhibitId
    ) {
        BinderResponse response = binderService.removeExhibitFromBinder(binderId, exhibitId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update binder lifecycle status",
               description = "Transitions status (DRAFT, UNDER_REVIEW, FINALIZED, SERVED). Once FINALIZED, status cannot revert.")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "400", description = "Attempted illegal status reversal on a locked binder")
    @PatchMapping("/{binderId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> updateStatus(
            @PathVariable Long binderId,
            @RequestParam("status") BinderStatus status
    ) {
        BinderResponse response = binderService.updateStatus(binderId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get exhibit binder by ID",
               description = "Retrieves binder details and all nested exhibits with Bates page ranges")
    @ApiResponse(responseCode = "200", description = "Binder returned")
    @ApiResponse(responseCode = "404", description = "Binder not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<BinderResponse> getBinderById(@PathVariable Long id) {
        BinderResponse response = binderService.getBinderById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List exhibit binders for a case",
               description = "Retrieves paginated exhibit binders for a specific legal case")
    @ApiResponse(responseCode = "200", description = "Page of binders returned")
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<BinderResponse>> getBindersByCaseId(
            @PathVariable Long caseId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<BinderResponse> binders = binderService.getBindersByCaseId(caseId, pageable);
        return ResponseEntity.ok(binders);
    }
}