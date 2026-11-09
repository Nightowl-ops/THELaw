package com.veritasvault.auth.service;

import com.veritasvault.auth.dto.request.AdminCreateUserRequest;
import com.veritasvault.auth.dto.response.UserProfileResponse;
import com.veritasvault.auth.exception.ConflictException;
import com.veritasvault.auth.exception.ResourceNotFoundException;
import com.veritasvault.auth.kafka.event.UserDeactivatedEvent;
import com.veritasvault.auth.kafka.producer.AuthKafkaProducer;
import com.veritasvault.auth.model.User;
import com.veritasvault.auth.model.enums.Role;
import com.veritasvault.auth.model.enums.UserStatus;
import com.veritasvault.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuthKafkaProducer kafkaProducer;

    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getUsers(Role role, UserStatus status, String search, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : "";
        return userRepository.searchUsers(role, status, query, pageable)
                .map(authService::mapToProfileResponse);
    }

    @Transactional
    public UserProfileResponse createUser(AdminCreateUserRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email address is already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .phoneNumber(request.getPhoneNumber())
                .organizationOrAgency(request.getOrganizationOrAgency())
                .barOrBadgeNumber(request.getBarOrBadgeNumber())
                .build();

        User saved = userRepository.save(user);
        return authService.mapToProfileResponse(saved);
    }

    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setStatus(status);
        User updated = userRepository.save(user);

        // If user is deactivated or suspended, broadcast Kafka event to downstream microservices
        if (status != UserStatus.ACTIVE) {
            log.info("Broadcasting deactivation event for User ID: {}", userId);
            try {
                kafkaProducer.sendUserDeactivatedEvent(UserDeactivatedEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .reason("Status updated to " + status + " by administrator")
                        .timestamp(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                log.error("Failed to publish UserDeactivatedEvent for User ID {}: {}", userId, ex.getMessage());
            }
        }

        return authService.mapToProfileResponse(updated);
    }

    @Transactional
    public UserProfileResponse updateUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setRole(newRole);
        User updated = userRepository.save(user);
        return authService.mapToProfileResponse(updated);
    }
}
