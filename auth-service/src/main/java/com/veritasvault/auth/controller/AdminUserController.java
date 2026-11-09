package com.veritasvault.auth.controller;

import com.veritasvault.auth.dto.request.AdminCreateUserRequest;
import com.veritasvault.auth.dto.response.UserProfileResponse;
import com.veritasvault.auth.model.enums.Role;
import com.veritasvault.auth.model.enums.UserStatus;
import com.veritasvault.auth.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

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

    @PostMapping
    public ResponseEntity<UserProfileResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        UserProfileResponse response = adminUserService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam("status") UserStatus status
    ) {
        UserProfileResponse response = adminUserService.updateUserStatus(userId, status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserProfileResponse> updateUserRole(
            @PathVariable Long userId,
            @RequestParam("role") Role role
    ) {
        UserProfileResponse response = adminUserService.updateUserRole(userId, role);
        return ResponseEntity.ok(response);
    }
}
