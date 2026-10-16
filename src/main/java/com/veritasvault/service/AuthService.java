package com.veritasvault.service;

import com.veritasvault.dto.request.ChangePasswordRequest;
import com.veritasvault.dto.request.ForgotPasswordRequest;
import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.dto.request.ResetPasswordRequest;
import com.veritasvault.dto.request.UpdateProfileRequest;
import com.veritasvault.dto.response.AuthResponse;
import com.veritasvault.dto.response.UserProfileResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ConflictException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.repository.UserRepository;
import com.veritasvault.security.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service managing user identity lifecycle, credential authentication,
 * token generation, password recovery, and profile metadata.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       EmailService emailService,
                       FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Registers a new user account with unverified email status.
     * Enforces server-side role assignment (defaulting to ROLE_CLIENT).
     *
     * @param request User registration payload
     * @return AuthResponse containing initial persisted user metadata
     * @throws ConflictException if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email address is already registered: " + normalizedEmail);
        }

        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

        // Server-enforced role: public self-registration defaults strictly to ROLE_CLIENT
        // Privileged roles (ATTORNEY, FORENSIC_EXAMINER, ADMIN) are provisioned through admin endpoints
        Role assignedRole = Role.ROLE_CLIENT;

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(tokenExpiry)
                .build();

        User savedUser = userRepository.save(user);

        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        return AuthResponse.builder()
                .token(null)
                .tokenType(null)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
    }

    /**
     * Validates account activation token and transitions user to verified status.
     */
    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized verification token"));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired. Please request a new activation link.");
        }

        user.setIsEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Email verified successfully! You may now log in to VeritasVault.";
    }

    /**
     * Authenticates user credentials via Spring Security and generates signed JWT.
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for authenticated principal"));

        String token = jwtUtils.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    /**
     * Initiates password reset by issuing a 1-hour secure token and sending email.
     */
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
        });

        // Always return success message to prevent user email enumeration
        return "If an account with that email exists, password reset instructions have been dispatched.";
    }

    /**
     * Resets user password using the issued reset token.
     */
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized password reset token"));

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired. Please request a new one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);

        return "Password has been successfully reset. You may now log in.";
    }

    /**
     * Changes password for an authenticated user after verifying the current password.
     */
    @Transactional
    public String changePassword(ChangePasswordRequest request, Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password does not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "Password changed successfully.";
    }

    /**
     * Retrieves the profile information for the authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));
        return mapToProfileResponse(user);
    }

    /**
     * Updates profile attributes for the authenticated user.
     */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request, Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        user.setFullName(request.getFullName().trim());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setOrganizationOrAgency(request.getOrganizationOrAgency());
        user.setBarOrBadgeNumber(request.getBarOrBadgeNumber());

        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    /**
     * Uploads and updates profile picture for the authenticated user.
     */
    @Transactional
    public UserProfileResponse updateProfilePicture(MultipartFile file, Long currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Profile picture file cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Uploaded file must be a valid image format (PNG, JPEG, WebP)");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        String relativePath = fileStorageService.storeFile(file, "profiles");
        user.setProfilePictureUrl(relativePath);

        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    /**
     * Helper to map User entity to UserProfileResponse DTO.
     */
    public UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .isEmailVerified(user.getIsEmailVerified())
                .profilePictureUrl(user.getProfilePictureUrl())
                .organizationOrAgency(user.getOrganizationOrAgency())
                .barOrBadgeNumber(user.getBarOrBadgeNumber())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}