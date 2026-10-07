package com.veritasvault.service;

import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.dto.response.AuthResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.repository.UserRepository;
import com.veritasvault.security.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/*
  Service orchestrating identity management, credential validation,
  token issuance, and account lifecycle transitions.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
    }

    /*
     Registers a new user account with an unverified status.
      Generates a 24-hour verification token and initiates email dispatch.

      @param request User registration payload containing credentials and assigned role
      @return AuthResponse containing persisted user metadata without an active session token
      @throws BadRequestException if the provided email is already bound to an existing account
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Enforce global email uniqueness before allocating credentials
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email address is already registered: " + request.getEmail());
        }

        // Generate an opaque, cryptographically random token for account activation
        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

        // Accounts default to ACTIVE status but remain gated by isEmailVerified = false
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .isEmailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(tokenExpiry)
                .build();

        User savedUser = userRepository.save(user);

        // Dispatch verification link asynchronously to avoid blocking the client request thread
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        // Security requirement: do not issue a Bearer token until email ownership is confirmed
        return AuthResponse.builder()
                .token(null)
                .tokenType(null)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
    }

    /*
      Validates a verification token and transitions the associated account to active/verified status.

      @param token Cryptographic UUID token supplied via email activation link
      @return Confirmation message upon successful verification
      @throws BadRequestException if the token does not exist or has exceeded its 24-hour lifetime
     */
    // transactional this rapes it all togeather so if an error happens halfway it roleback all of it so no problem might accour
    // and incomplete data gets added to the table
    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized verification token"));

        // Enforce strict token expiration to mitigate link interception risks
        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired. Please request a new activation link.");
        }

        // Activate account and nullify single-use token fields to prevent replay
        user.setIsEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Email verified successfully! You may now log in to VeritasVault.";
    }

    /*
      Authenticates user credentials via Spring Security and generates a signed JWT.

      @param request Login credentials (email and plaintext password)
      @return AuthResponse containing the user profile and signed Bearer JWT
      @throws DisabledException if the account has not verified its email address
      @throws BadCredentialsException if the email or password is invalid
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        // Delegate authentication to Spring Security DAO provider (handles BCrypt matching)
        // If isEmailVerified == false or status == INACTIVE, MyUserDetails.isEnabled() triggers DisabledException
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for authenticated principal"));

        // Generate signed HMAC-SHA256 token encoding identity and role claims
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
}