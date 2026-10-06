# 01 - Authentication, Authorization & Identity Lifecycle

## 1. Functional Overview
The Authentication module manages user onboarding, cryptographic credential hashing, email-based identity verification, and stateless JSON Web Token (JWT) session generation.

To comply with legal audit requirements, accounts cannot perform operations or authenticate immediately upon registration. Every new account is created with `isEmailVerified = false` and must confirm identity via a single-use, 24-hour cryptographic token sent via SMTP. Once verified, authentication issues signed HMAC-SHA256 JWT tokens with role-based claims.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.User`
    - `com.veritasvault.model.enums.Role` (`ROLE_ADMIN`, `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_CLIENT`)
    - `com.veritasvault.model.enums.UserStatus` (`ACTIVE`, `INACTIVE`)
- **Repositories:**
    - `com.veritasvault.repository.UserRepository`
- **Security Infrastructure:**
    - `com.veritasvault.security.SecurityConfiguration`
    - `com.veritasvault.security.JwtUtils`
    - `com.veritasvault.security.JwtRequestFilter`
    - `com.veritasvault.security.MyUserDetails`
    - `com.veritasvault.security.MyUserDetailsService`
- **Services:**
    - `com.veritasvault.service.AuthService`
    - `com.veritasvault.service.EmailService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.AuthController`
    - `com.veritasvault.dto.request.RegisterRequest`
    - `com.veritasvault.dto.request.LoginRequest`
    - `com.veritasvault.dto.response.AuthResponse`

---

## 3. Workflow & Logic Sequence

### A. Registration Flow (`POST /api/auth/register`)
1. Client submits full name, email, plaintext password, and intended role.
2. `AuthService.register()` checks `userRepository.existsByEmail()`. If already taken, throws `BadRequestException`.
3. Hashes password using BCrypt (`PasswordEncoder`).
4. Generates a random activation UUID: `UUID.randomUUID().toString()`.
5. Sets expiration to `LocalDateTime.now().plusHours(24)`.
6. Saves user with `isEmailVerified = false` and `status = ACTIVE`.
7. Calls `EmailService.sendVerificationEmail()` to dispatch an activation URL:
   ```text
   http://localhost:8085/api/auth/verify?token=<UUID>