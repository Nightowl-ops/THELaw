package com.veritasvault.auth.bdd;

import com.veritasvault.auth.dto.request.RegisterRequest;
import com.veritasvault.auth.dto.response.AuthResponse;
import com.veritasvault.auth.model.User;
import com.veritasvault.auth.repository.UserRepository;
import com.veritasvault.auth.security.JwtUtils;
import com.veritasvault.auth.service.AuthService;
import com.veritasvault.auth.service.EmailService;
import com.veritasvault.auth.service.FileStorageService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AuthStepDefinitions {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
    private final JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
    private final EmailService emailService = Mockito.mock(EmailService.class);
    private final FileStorageService fileStorageService = Mockito.mock(FileStorageService.class);

    private final AuthService authService;
    private RegisterRequest request;
    private AuthResponse response;

    public AuthStepDefinitions() {
        this.authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtUtils,
                emailService,
                fileStorageService
        );
    }

    @Given("a registration request with email {string} and full name {string}")
    public void a_registration_request_with_email_and_full_name(String email, String fullName) {
        request = new RegisterRequest();
        request.setEmail(email);
        request.setFullName(fullName);
    }

    @And("a valid secure password {string}")
    public void a_valid_secure_password(String password) {
        request.setPassword(password);
    }

    @When("the user submits the registration request")
    public void the_user_submits_the_registration_request() {
        when(userRepository.existsByEmail(request.getEmail().toLowerCase().trim())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-test-password-xyz");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(99L);
            return u;
        });

        response = authService.register(request);
    }

    @Then("the registration should succeed")
    public void the_registration_should_succeed() {
        assertNotNull(response);
        assertEquals(99L, response.getUserId());
    }

    @And("the registered user email should be {string}")
    public void the_registered_user_email_should_be(String expectedEmail) {
        assertEquals(expectedEmail, response.getEmail());
    }

    @And("the user role should be {string}")
    public void the_user_role_should_be(String expectedRole) {
        assertEquals(expectedRole, response.getRole().name());
    }
}
