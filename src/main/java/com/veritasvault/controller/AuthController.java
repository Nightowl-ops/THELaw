package com.veritasvault.controller;

import com.veritasvault.dto.request.ChangePasswordRequest;
import com.veritasvault.dto.request.ForgotPasswordRequest;
import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.dto.request.ResetPasswordRequest;
import com.veritasvault.dto.request.UpdateProfileRequest;
import com.veritasvault.dto.response.AuthResponse;
import com.veritasvault.dto.response.UserProfileResponse;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication & Identity", description = "Endpoints for user registration, login, email verification, password management, and user profile")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register new account", description = "Registers a new user account defaulting to ROLE_CLIENT and dispatches an activation email")
    @ApiResponse(responseCode = "201", description = "Account created; activation token dispatched")
    @ApiResponse(responseCode = "400", description = "Validation failure or weak password")
    @ApiResponse(responseCode = "409", description = "Email address already registered")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Verify email address", description = "Validates the single-use 24-hour cryptographic token and activates the account")
    @ApiResponse(responseCode = "200", description = "Email verified successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or expired verification token")
    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        String result = authService.verifyEmail(token);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "User login", description = "Authenticates user credentials and generates a signed stateless JWT Bearer token")
    @ApiResponse(responseCode = "200", description = "Authenticated successfully, returns Bearer token")
    @ApiResponse(responseCode = "401", description = "Invalid credentials or unverified email")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Forgot password request", description = "Generates a 1-hour password reset token and sends recovery email")
    @ApiResponse(responseCode = "200", description = "Instructions dispatched if account exists")
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String result = authService.forgotPassword(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Reset password with token", description = "Resets user password using the token sent via email")
    @ApiResponse(responseCode = "200", description = "Password reset successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token, or weak password")
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String result = authService.resetPassword(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Change password", description = "Changes password for the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "Password updated successfully")
    @ApiResponse(responseCode = "400", description = "Current password incorrect or new password weak")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Get current user profile", description = "Retrieves the authenticated user's profile metadata")
    @ApiResponse(responseCode = "200", description = "User profile returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal MyUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        Long currentUserId = userDetails.getUser().getId();
        UserProfileResponse response = authService.getCurrentUserProfile(currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update user profile", description = "Updates profile metadata for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Upload profile picture", description = "Uploads a new profile image for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Profile picture updated")
    @ApiResponse(responseCode = "400", description = "Invalid file or non-image format")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Get user profile picture", description = "Streams the profile picture for the specified user ID")
    @ApiResponse(responseCode = "200", description = "Profile picture image stream")
    @ApiResponse(responseCode = "404", description = "User or picture not found")
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

    @Operation(summary = "Get current user profile picture", description = "Streams the profile picture for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Profile picture image stream")
    @ApiResponse(responseCode = "404", description = "Picture not found")
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