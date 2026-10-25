# AuthService Explanation

## What is AuthService?

In a high-security legal evidence and forensic platform, identity management is the first and most critical line of defense. 

`AuthService` serves as the primary security gateway for VeritasVault. It governs how users enter the system, how their credentials are authenticated, and how their identity is carried across every subsequent API request. 

It manages four major operational pillars:
1. **User Registration & Email Verification:** Registers new users with strict least-privilege defaults (`ROLE_CLIENT`) and issues 24-hour cryptographic email verification tokens.
2. **Stateless JWT Authentication:** Authenticates credentials via Spring Security's `AuthenticationManager` and issues signed, tamper-proof JSON Web Tokens (JWT).
3. **Anti-Enumeration Password Recovery:** Handles password reset requests using defensive API patterns that never reveal whether an email address exists in the system.
4. **Decoupled Profile Management:** Manages user profile metadata, password updates, and profile photo uploads with disk-isolated binary storage.

---

## Variables Being Used and Their Reasons:

* **`userRepository`**: Directly communicates with the `users` table in PostgreSQL. Performs email uniqueness checks, user lookups by email, verification token lookups, and profile persistence.
* **`passwordEncoder`**: BCrypt cryptographic hashing bean (`BCryptPasswordEncoder`). Ensures that raw passwords are never stored in the database or logged in application memory.
* **`authenticationManager`**: Spring Security's central authentication coordinator. Verifies the user's email and password against stored database hashes before granting authentication.
* **`jwtUtils`**: Utility component responsible for generating, signing, and parsing HMAC-SHA-256 JSON Web Tokens (JWT) containing user claims and roles.
* **`emailService`**: Outbound SMTP email service. Sends activation URLs and one-time password reset tokens to the user's inbox.
* **`fileStorageService`**: Disk file storage manager. Handles saving user avatar images to the `uploads/profiles/` disk directory, keeping large image binaries out of PostgreSQL.

---

## Methods and Their Implementation Explained:

### 1. `register(RegisterRequest request)`: [Public Self-Registration]
* **Email Normalization & Duplicate Prevention:**
  * Converts the input email to lowercase and trims whitespace: `request.getEmail().toLowerCase().trim()`.
  * Queries `userRepository.existsByEmail(normalizedEmail)`. If found, immediately throws `ConflictException` (**`409 Conflict`**) to prevent account collisions.
* **Token & Role Assignment:**
  * Generates an unguessable activation token: `UUID.randomUUID().toString()`.
  * Sets token expiration to 24 hours: `LocalDateTime.now().plusHours(24)`.
  * **Strict Role Restriction:** Hardcodes `assignedRole = Role.ROLE_CLIENT`. Public self-registration is never allowed to claim elevated roles (`ROLE_ATTORNEY`, `ROLE_ADMIN`, `ROLE_FORENSIC_EXAMINER`).
* **Database Persistence:**
  * Hashes the password using `passwordEncoder.encode(request.getPassword())`.
  * Sets `isEmailVerified = false` and `status = UserStatus.ACTIVE`.
  * Saves the entity to PostgreSQL.
* **Activation Email Dispatch:**
  * Calls `emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken)` to send the activation link.
* **Return:** Returns `AuthResponse` with user ID, name, email, and role, but with `token = null` (login is forbidden until verified).

### 2. `verifyEmail(String token)`: [Account Activation]
* Queries `userRepository.findByVerificationToken(token)`. If not found, throws `BadRequestException` ("Invalid or unrecognized verification token").
* **Expiration Validation:** Checks if `verificationTokenExpiry.isBefore(LocalDateTime.now())`. If expired, rejects activation.
* **Account Unlock:**
  * Sets `isEmailVerified = true`.
  * Clears `verificationToken = null` and `verificationTokenExpiry = null` so the single-use token can never be replayed.
  * Saves changes to the database.

### 3. `login(LoginRequest request)`: [Credential Verification & JWT Issuance]
* Normalizes the incoming email.
* Passes the email and raw password into `authenticationManager.authenticate(...)`. Spring Security checks the credentials against the database BCrypt hash.
* If authentication succeeds, retrieves the user details from the database.
* Calls `jwtUtils.generateToken(userDetails)` to produce a signed, stateless JWT token.
* Returns `AuthResponse` containing the Bearer token, expiration, user ID, full name, email, and assigned role.

### 4. `forgotPassword(ForgotPasswordRequest request)`: [Anti-Enumeration Defense]
* Normalizes the target email address.
* Looks up the user in the database:
  * **If user exists:** Generates a 1-hour reset token (`UUID.randomUUID()`), stores it on the user entity with `plusHours(1)` expiry, and calls `emailService.sendPasswordResetEmail(...)`.
  * **If user does NOT exist:** Does nothing silently.
* **The Defense:** Returns the exact same response string in both scenarios: *"If an account with that email exists, password reset instructions have been dispatched."* 
  * This prevents malicious actors from brute-forcing emails to discover which attorneys or law enforcement officers have accounts on the system.

### 5. `resetPassword(ResetPasswordRequest request)`: [Password Token Consumption]
* Finds the user by `findByPasswordResetToken(request.getToken())`.
* Verifies token has not expired (`passwordResetTokenExpiry.isBefore(LocalDateTime.now())`).
* Hashes the new password: `passwordEncoder.encode(request.getNewPassword())`.
* Wipes `passwordResetToken` and `passwordResetTokenExpiry` to prevent token reuse.
* Saves the updated user entity.

### 6. `changePassword(ChangePasswordRequest request, Long currentUserId)`: [Authenticated Password Update]
* Loads the authenticated user from `userRepository.findById(currentUserId)`.
* Verifies the user's existing password via `passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())`.
* If incorrect, rejects with `BadRequestException` ("Current password does not match").
* Encodes and saves the new password.

### 7. `getCurrentUserProfile(Long currentUserId)` & `updateProfile(...)`
* `getCurrentUserProfile`: Fetches the user profile and maps it to a sanitized `UserProfileResponse` DTO.
* `updateProfile`: Allows authenticated users to update their editable contact information: full name, phone number, organization/agency, and state bar or police badge number.

### 8. `updateProfilePicture(MultipartFile file, Long currentUserId)`: [Avatar Ingestion]
* Validates that the uploaded file is not empty and has a MIME type starting with `image/` (e.g., `image/png`, `image/jpeg`, `image/webp`).
* Calls `fileStorageService.storeFile(file, "profiles")` to save the image to disk.
* Saves the relative file path string (e.g. `profiles/avatar-uuid.png`) on the user record in PostgreSQL.

### 9. `loadProfilePicture(Long userId)`: [Avatar Retrieval]
* Retrieves the user record, extracts their `profilePictureUrl`, and delegates to `fileStorageService.loadAsResource(...)` to return a Spring `Resource` stream.

### 10. `mapToProfileResponse(User user)`: [DTO Mapper]
* Sanitizes the `User` database entity into a clean `UserProfileResponse` DTO.
* Strips all internal security fields (password hash, verification tokens, reset tokens) before returning data to client browsers.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why is public registration restricted to `ROLE_CLIENT`?** | Legal and forensic evidence platforms manage sensitive chain of custody. Allowing self-assigned `ROLE_ATTORNEY` or `ROLE_FORENSIC_EXAMINER` would allow unauthorized outsiders to access confidential discovery or alter evidence records. |
| **Why return a generic response in `forgotPassword`?** | Returning "Email not found" allows attackers to test lists of email addresses (User Enumeration Attack) to discover which prosecutors or detectives use the system. Returning an identical message neutralizes this reconnaissance. |
| **Why use JWT instead of HTTP Sessions?** | JWTs are stateless and tamper-proof. The server does not need to maintain server-side session memory across multiple server instances, making horizontal scaling simple and secure. |
