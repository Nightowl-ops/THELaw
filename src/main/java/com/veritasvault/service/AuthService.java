package com.veritasvault.service;

import com.veritasvault.dto.request.AuthResponse;
import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.model.User;
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

import java.time.LocalDateTime;
import java.util.UUID;

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

    public AuthResponse register(RegisterRequest request) {
        // 1. Verify email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use: " + request.getEmail());
        }

        // 2. Generate verification token and 24-hour expiration
        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);

        // 3. Persist user with isEmailVerified = false
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .isEmailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(expiry)
                .build();

        User savedUser = userRepository.save(user);

        // 4. Send the verification email
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        // 5. Return AuthResponse without a token (token is null until email is verified)
        return AuthResponse.builder()
                .token(null)
                .tokenType(null)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification token has expired. Please request a new one.");
        }

        // Activate user email verification
        user.setIsEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Email verified successfully! You can now log in.";
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate user credentials via AuthenticationManager
        // If isEmailVerified == false, MyUserDetails.isEnabled() returns false, throwing DisabledException
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

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