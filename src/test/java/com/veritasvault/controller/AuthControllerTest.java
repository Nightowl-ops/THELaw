package com.veritasvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veritasvault.dto.request.LoginRequest;
import com.veritasvault.dto.request.RegisterRequest;
import com.veritasvault.dto.response.AuthResponse;
import com.veritasvault.model.enums.Role;
import com.veritasvault.security.JwtRequestFilter;
import com.veritasvault.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Disables filters for focused controller unit test
class AuthControllerTest {

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
        request.setRole(Role.ROLE_ATTORNEY);

        AuthResponse authResponse = AuthResponse.builder()
                .userId(1L)
                .email("john.doe@veritasvault.com")
                .fullName("John Doe")
                .role(Role.ROLE_ATTORNEY)
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
        request.setEmail("invalid-email-format"); // Missing full name, short password, no role

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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
    @DisplayName("GET /api/auth/verify - Should return 200 with success confirmation message")
    void verifyEmail_ReturnsOk() throws Exception {
        when(authService.verifyEmail("valid-uuid")).thenReturn("Email verified successfully!");

        mockMvc.perform(get("/api/auth/verify").param("token", "valid-uuid"))
                .andExpect(status().isOk());
    }
}