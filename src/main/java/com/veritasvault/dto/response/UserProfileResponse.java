package com.veritasvault.dto.response;

import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Role role;
    private UserStatus status;
    private Boolean isEmailVerified;
    private String profilePictureUrl;
    private String organizationOrAgency;
    private String barOrBadgeNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}