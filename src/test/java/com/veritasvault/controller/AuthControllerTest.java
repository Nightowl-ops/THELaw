package com.veritasvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veritasvault.dto.request.ChangePasswordRequest;
import com.veritasvault.dto.request.ForgotPasswordRequest;
import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.dto.request.ResetPasswordRequest;
import com.veritasvault.dto.response.AuthResponse;
import com.veritasvault.exception.ConflictException;
import com.veritasvault.exception.GlobalExceptionHandler;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.security.JwtRequestFilter;
import com.veritasvault.security.MyUserDetails;
import com.veritasvault.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, AuthControllerTest.SecurityTestConfig.class})
@AutoConfigureMockMvc(addFilters = false) // Disables filters for focused controller unit test
class AuthControllerTest {

    @TestConfiguration
    static class SecurityTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtRequestFilter jwtRequestFilter;

    @Test
    @DisplayName("POST /api/auth/register - Should return 201 Created on valid registration")
    void register_ValidPayload_ReturnsCreated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john.doe@veritasvault.com");
        request.setPassword("Password123!");
        request.setRole(Role.ROLE_CLIENT);

        AuthResponse authResponse = AuthResponse.builder()
                .userId(1L)
                .email("john.doe@veritasvault.com")
                .fullName("John Doe")
                .role(Role.ROLE_CLIENT)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.email").value("john.doe@veritasvault.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 when fields fail validation")
    void register_InvalidPayload_ReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email-format"); // Missing full name, short password

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 when password lacks complexity")
    void register_WeakPassword_ReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Jane Doe");
        request.setEmail("jane.doe@veritasvault.com");
        request.setPassword("weakpass"); // Fails pattern (no uppercase, no digit, no symbol)

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 409 Conflict when email already registered")
    void register_DuplicateEmail_ReturnsConflict409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("existing@veritasvault.com");
        request.setPassword("Password123!");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("An account with this email address already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email address already exists"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 200 OK with Bearer token")
    void login_Success_ReturnsOk() throws Exception {
        LoginRequest request = new LoginRequest("user@veritasvault.com", "Password123!");

        AuthResponse authResponse = AuthResponse.builder()
                .token("mock-signed-jwt")
                .tokenType("Bearer")
                .email("user@veritasvault.com")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 401 Unauthorized on bad credentials")
    void login_BadCredentials_ReturnsUnauthorized401() throws Exception {
        LoginRequest request = new LoginRequest("user@veritasvault.com", "WrongPassword123!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("GET /api/auth/verify - Should return 200 with success confirmation message")
    void verifyEmail_ReturnsOk() throws Exception {
        when(authService.verifyEmail("valid-uuid")).thenReturn("Email verified successfully!");

        mockMvc.perform(get("/api/auth/verify").param("token", "valid-uuid"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - Should return 200 OK")
    void forgotPassword_Success_ReturnsOk() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@veritasvault.com");
        when(authService.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn("If an account with that email exists, password reset instructions have been dispatched.");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("If an account with that email exists, password reset instructions have been dispatched."));
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Should return 200 OK")
    void resetPassword_Success_ReturnsOk() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-reset-token", "NewPassword123!");
        when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn("Password has been successfully reset. You may now log in.");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password has been successfully reset. You may now log in."));
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Should return 200 OK for authenticated user")
    void changePassword_Success_ReturnsOk() throws Exception {
        User user = User.builder()
                .id(5L)
                .email("user@veritasvault.com")
                .fullName("Test User")
                .role(Role.ROLE_CLIENT)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();
        MyUserDetails userDetails = new MyUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");
        when(authService.changePassword(any(ChangePasswordRequest.class), eq(5L)))
                .thenReturn("Password changed successfully.");

        try {
            mockMvc.perform(post("/api/auth/change-password")
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Password changed successfully."));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}