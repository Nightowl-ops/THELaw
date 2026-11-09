package com.veritasvault.auth.controller;

import com.veritasvault.auth.dto.request.ChangePasswordRequest;
import com.veritasvault.auth.dto.request.ForgotPasswordRequest;
import com.veritasvault.auth.dto.request.LoginRequest;
import com.veritasvault.auth.dto.request.RegisterRequest;
import com.veritasvault.auth.dto.request.ResetPasswordRequest;
import com.veritasvault.auth.dto.request.UpdateProfileRequest;
import com.veritasvault.auth.dto.response.AuthResponse;
import com.veritasvault.auth.dto.response.UserProfileResponse;
import com.veritasvault.auth.security.MyUserDetails;
import com.veritasvault.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        String result = authService.verifyEmail(token);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String result = authService.forgotPassword(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String result = authService.resetPassword(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        Long currentUserId = userDetails.getUser().getId();
        String result = authService.changePassword(request, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal MyUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        Long currentUserId = userDetails.getUser().getId();
        UserProfileResponse response = authService.getCurrentUserProfile(currentUserId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        Long currentUserId = userDetails.getUser().getId();
        UserProfileResponse response = authService.updateProfile(request, currentUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/profile/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal MyUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        Long currentUserId = userDetails.getUser().getId();
        UserProfileResponse response = authService.updateProfilePicture(file, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/picture")
    public ResponseEntity<Resource> getUserProfilePicture(@PathVariable Long userId) {
        Resource resource = authService.loadProfilePicture(userId);
        MediaType mediaType = determineMediaType(resource.getFilename());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate")
                .body(resource);
    }

    @GetMapping("/profile/picture")
    public ResponseEntity<Resource> getCurrentUserProfilePicture(@AuthenticationPrincipal MyUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        return getUserProfilePicture(userDetails.getUser().getId());
    }

    private MediaType determineMediaType(String filename) {
        if (filename == null) return MediaType.IMAGE_JPEG;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
