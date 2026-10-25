package com.veritasvault.service;

import com.veritasvault.dto.request.AdminCreateUserRequest;
import com.veritasvault.dto.response.UserProfileResponse;
import com.veritasvault.exception.ConflictException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

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
