package com.veritasvault.controller;

import com.veritasvault.dto.request.AdminCreateUserRequest;
import com.veritasvault.dto.response.UserProfileResponse;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.service.AdminUserService;
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
import org.springframework.web.bind.annotation.*;

/**
 * Administrative controller for user provisioning, role promotion,
 * and account status management (including soft delete).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@Tag(name = "Admin User Management", description = "Administrative endpoints for managing accounts, roles, and soft deletions")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List users with pagination and filtering",
               description = "Returns paginated user profiles with optional role, status, and text search filters")
    @ApiResponse(responseCode = "200", description = "Page of user profiles returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_ADMIN")
    @GetMapping
    public ResponseEntity<Page<UserProfileResponse>> getUsers(
            @RequestParam(value = "role", required = false) Role role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<UserProfileResponse> users = adminUserService.getUsers(role, status, search, pageable);
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Create user account with role",
               description = "Allows administrators to provision users across any role (e.g. ATTORNEY, FORENSIC_EXAMINER)")
    @ApiResponse(responseCode = "201", description = "User created successfully")
    @ApiResponse(responseCode = "400", description = "Validation failure or weak password")
    @ApiResponse(responseCode = "409", description = "Email address already registered")
    @PostMapping
    public ResponseEntity<UserProfileResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        UserProfileResponse response = adminUserService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Update account status (Soft Delete)",
               description = "Toggles user status between ACTIVE and INACTIVE to perform soft-delete without breaking foreign keys")
    @ApiResponse(responseCode = "200", description = "User status updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable Long id,
            @RequestParam("status") UserStatus status
    ) {
        UserProfileResponse response = adminUserService.updateUserStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update account security role",
               description = "Modifies an existing user's authorization role")
    @ApiResponse(responseCode = "200", description = "User role updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserProfileResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam("role") Role role
    ) {
        UserProfileResponse response = adminUserService.updateUserRole(id, role);
        return ResponseEntity.ok(response);
    }
}
