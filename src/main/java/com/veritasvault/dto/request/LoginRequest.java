package com.veritasvault.dto.request;


import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid email address")
    private String email;

    @NotBlank(message = "password is required ")
    private String password;

}
