package com.veritasvault.dto.request;

import com.veritasvault.model.enums.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AuthResponse {

    private String token;
    @Builder.Default
    private String tokenType="Bearer";

    private Long userId;
    private String email;
    private String fullName;
    private Role role;
}
