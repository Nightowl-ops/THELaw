package com.veritasvault.dto.request;


import com.veritasvault.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class RegisterRequest {

    // this will be used to validate the user input before injecting it to the system

   @NotBlank(message = "Full name is required")
    @Size(min=2,max=100, message="full name must be between 2 and 100 charecters")
    private String fullName;


   @NotBlank(message = "Email is required")
    @Email(message = "email must be valid email address")
    private String email;

   @NotBlank(message= "Email is required")
    @Size(min=8, max=100, message = "password must be at least 8 charecters long")
    private String password;


@NotNull(message="should be a role avilable")

    private Role role  ;





}
