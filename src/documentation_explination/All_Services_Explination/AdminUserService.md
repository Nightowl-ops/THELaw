# AdminUserService Explanation

## What is AdminUserService?

In a legal and forensic application, access permissions must adhere to the principle of least privilege. 

If any random user on the internet could register an account and instantly gain access to crime scene physical evidence, police reports, or confidential trial documents, the entire integrity of the justice system would be compromised.

For this reason, VeritasVault enforces strict role segregation:
* **Public Self-Registration:** Only grants `ROLE_CLIENT` (restricted view-only access to their specific case files).
* **Elevated Operational Roles:** Only an authenticated System Administrator (`ROLE_ADMIN`) has the power to provision and manage privileged roles (`ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN`).

`AdminUserService` handles this administrative user lifecycle, including elevated user creation, paginated directory searches, role elevations, and account suspension/activation.

---

## Variables Being Used and Their Reasons:

* **`userRepository`**: Directly executes queries against the `users` table in PostgreSQL. Performs complex multi-criteria searches, email uniqueness checks, and updates.
* **`passwordEncoder`**: BCrypt hashing bean. Ensures that when an admin provisions a new user with a temporary or permanent password, the raw text password is mathematically hashed before touching the database.
* **`authService`**: Leveraged for its `mapToProfileResponse` conversion logic. Keeps code DRY (Don't Repeat Yourself) by reusing a single, secure mapping function that excludes sensitive security tokens and password hashes from API responses.

---

## Methods and Their Implementation Explained:

### 1. `getUsers(Role role, UserStatus status, String search, Pageable pageable)`: [Directory Search]
* Annotated with `@Transactional(readOnly = true)`.
* Trims and normalizes the search query keyword.
* Calls `userRepository.searchUsers(role, status, query, pageable)`.
* Allows administrators in the frontend dashboard to filter the entire user registry by role (e.g. all Forensic Examiners), by status (e.g. all Suspended accounts), or search by name/email/badge number.
* Maps the resulting Spring `Page<User>` to `Page<UserProfileResponse>`.

### 2. `createUser(AdminCreateUserRequest request)`: [Administrative Provisioning]
* **Email Normalization & Uniqueness Check:**
  * Converts the input email to lowercase and trims whitespace.
  * Calls `userRepository.existsByEmail(normalizedEmail)`. If an account already exists, rejects the operation with `ConflictException` (**`409 Conflict`**).
* **Security & Verification Bypass:**
  * Hashes the password using `passwordEncoder.encode(request.getPassword())`.
  * Sets `isEmailVerified(true)` and `status(UserStatus.ACTIVE)`.
  * **Why?** Since a verified system administrator is explicitly provisioning this government or law firm employee, the user does not need to wait for public email self-verification.
* **Legal Credentials:**
  * Captures official professional identifiers:
    * `barOrBadgeNumber`: State Bar License number for attorneys, or Police Badge ID for law enforcement examiners.
    * `organizationOrAgency`: Law firm name, district attorney office, or forensic crime lab agency.
* **Persistence:** Saves the user entity and returns the sanitized user profile.

### 3. `updateUserStatus(Long userId, UserStatus status)`: [Account Quarantine / Revocation]
* Finds the user by ID or throws `ResourceNotFoundException`.
* Updates the account status (e.g., `ACTIVE`, `SUSPENDED`, `INACTIVE`).
* **Security Impact:** If an investigator resigns or is suspected of misconduct, setting status to `SUSPENDED` immediately blocks their ability to log in or obtain JWT tokens via `AuthService.login`.

### 4. `updateUserRole(Long userId, Role newRole)`: [Privilege Elevation]
* Finds the target user.
* Modifies their authorization tier (e.g. elevating a paralegal from `ROLE_CLIENT` to `ROLE_ATTORNEY`, or assigning an examiner `ROLE_FORENSIC_EXAMINER`).
* Saves the entity and returns the updated profile.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why not allow self-registration for Attorneys or Forensic Examiners?** | Allowing anyone to sign up with elevated permissions would permit unauthorized individuals to alter custody logs or view sealed evidence. Elevated roles must be provisioned administratively. |
| **Why are admin-created accounts automatically marked `isEmailVerified = true`?** | In enterprise and law enforcement deployments, IT administrators provision verified internal accounts directly, so requiring an email verification token loop is redundant and delays onboarding. |
| **What happens to active sessions when an admin suspends a user?** | The user cannot authenticate again at `AuthService.login` (which checks `user.getStatus() == UserStatus.ACTIVE`). In tandem, role or status changes immediately restrict further operational actions. |
