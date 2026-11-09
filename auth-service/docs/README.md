# Veritas Identity & Authentication Microservice (`auth-service`)

> **Port:** `8081`  
> **Database:** `veritas_auth_db`  
> **Package:** `com.veritasvault.auth`  

This microservice is the sole security and identity authority for VeritasVault. It manages all human credentials, roles, password lifecycles, JWT token issuing with embedded claims, and Kafka user deactivation broadcasts.

---

## 🔑 Pre-Seeded Testing Accounts
All accounts are pre-verified with the default password: **`Password123!`**

| Role | Email | Password | Purpose |
| :--- | :--- | :--- | :--- |
| **ROLE_ADMIN** | `admin@veritasvault.com` | `Password123!` | Managing accounts, soft-deletions, role updates |
| **ROLE_ATTORNEY** | `attorney@veritasvault.com` | `Password123!` | Case filing, discovery, evidence access |
| **ROLE_FORENSIC_EXAMINER** | `examiner@veritasvault.com` | `Password123!` | Lab bench reservations, SHA-256 evidence check-ins |
| **ROLE_CLIENT** | `client@veritasvault.com` | `Password123!` | Public registered client accounts |

---

## 📡 Complete API Reference

---

### 1. Public Endpoints (No Token Required)

These endpoints are open to all unauthenticated requests.

#### A. User Login
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/login`
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "email": "attorney@veritasvault.com",
    "password": "Password123!"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": 2,
    "email": "attorney@veritasvault.com",
    "fullName": "Sarah Jenkins, Esq.",
    "role": "ROLE_ATTORNEY"
  }
  ```

#### B. Public User Registration
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/register`
* **Rules:** Every public registration is strictly hard-coded to **`ROLE_CLIENT`**. A public user cannot choose attorney or examiner roles.
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "fullName": "John Doe",
    "email": "john.doe@domain.com",
    "password": "Password123!"
  }
  ```
* **Success Response (`201 CREATED`):** Returns the newly created user object. Requires email verification before login.

#### C. Verify Email Address
* **Method:** `GET`
* **URL:** `http://localhost:8081/api/auth/verify?token=UUID_VERIFICATION_TOKEN`
* **Success Response (`200 OK`):** `"Email verified successfully! You may now log in to VeritasVault."`

#### D. Forgot Password Request
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/forgot-password`
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "email": "client@veritasvault.com"
  }
  ```
* **Success Response (`200 OK`):** Dispatches a 1-hour single-use reset token.

#### E. Reset Password with Token
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/reset-password`
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "token": "UUID_RESET_TOKEN",
    "newPassword": "NewSecurePassword123!"
  }
  ```
* **Success Response (`200 OK`):** `"Password has been successfully reset. You may now log in."`

#### F. View Any User's Avatar Picture
* **Method:** `GET`
* **URL:** `http://localhost:8081/api/auth/users/{userId}/picture`
* **Success Response (`200 OK`):** Returns the binary image stream (`image/jpeg`, `image/png`, etc.).

---

### 2. User Profile Endpoints (Requires Bearer Token)

To call these endpoints, you must include your JWT token in the request headers:  
`Authorization: Bearer <your_jwt_token>`

#### A. Get Current Authenticated Profile
* **Method:** `GET`
* **URL:** `http://localhost:8081/api/auth/me`
* **Success Response (`200 OK`):**
  ```json
  {
    "id": 2,
    "email": "attorney@veritasvault.com",
    "fullName": "Sarah Jenkins, Esq.",
    "role": "ROLE_ATTORNEY",
    "status": "ACTIVE",
    "isEmailVerified": true,
    "phoneNumber": null,
    "organizationOrAgency": null,
    "barOrBadgeNumber": null,
    "createdAt": "2026-11-09T06:24:17",
    "updatedAt": "2026-11-09T06:24:17"
  }
  ```

#### B. Update Profile Information
* **Method:** `PUT`
* **URL:** `http://localhost:8081/api/auth/profile`
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "fullName": "Sarah Jenkins, Senior Partner",
    "phoneNumber": "+1-555-0199",
    "organizationOrAgency": "Veritas Law Group LLC",
    "barOrBadgeNumber": "BAR-44910"
  }
  ```
* **Success Response (`200 OK`):** Returns the updated profile.

#### C. Change Password
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/change-password`
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "currentPassword": "Password123!",
    "newPassword": "NewPassword123!"
  }
  ```
* **Success Response (`200 OK`):** `"Password changed successfully."`

#### D. Upload Profile Avatar Picture
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/auth/profile/picture`
* **Body Type:** `form-data`
  * **Key:** `file` (File type)
  * **Value:** *(Select an image from disk)*
* **Success Response (`200 OK`):** Returns updated profile with `profilePictureUrl`.

#### E. View Own Profile Picture
* **Method:** `GET`
* **URL:** `http://localhost:8081/api/auth/profile/picture`
* **Success Response (`200 OK`):** Streams the uploaded avatar file.

---

### 3. Administrator Endpoints (Requires `ROLE_ADMIN`)

These endpoints are strictly protected by `@PreAuthorize("hasRole('ROLE_ADMIN')")`.  
You must log in as `admin@veritasvault.com` and use that token in your header:  
`Authorization: Bearer <admin_jwt_token>`

#### A. List Users with Filters & Pagination
* **Method:** `GET`
* **URL:** `http://localhost:8081/api/admin/users`
* **Optional Query Parameters:**
  * `?role=ROLE_ATTORNEY` *(Filter by role)*
  * `?status=ACTIVE` *(Filter by status)*
  * `?search=Sarah` *(Searches name and email)*
  * `?page=0&size=10` *(Pagination)*
* **Example:** `http://localhost:8081/api/admin/users?role=ROLE_ATTORNEY&search=Sarah`
* **Success Response (`200 OK`):** Paginated JSON list of user profiles.

#### B. Admin Provisioning (Create Attorney / Examiner / Admin)
* **Method:** `POST`
* **URL:** `http://localhost:8081/api/admin/users`
* **Rules:** Allows administrators to create pre-verified staff accounts across any role, skipping public registration constraints.
* **Headers:** `Content-Type: application/json`
* **Body:**
  ```json
  {
    "fullName": "Detective James Vance",
    "email": "jvance@veritasvault.com",
    "password": "Password123!",
    "role": "ROLE_FORENSIC_EXAMINER",
    "phoneNumber": "+1-555-8833",
    "organizationOrAgency": "State Cyber Forensics Division",
    "barOrBadgeNumber": "BADGE-9912"
  }
  ```
* **Success Response (`201 CREATED`):** User created and immediately marked `isEmailVerified: true` and `status: ACTIVE`.

#### C. Soft-Deactivate User (Kafka Event Trigger)
* **Method:** `PATCH`
* **URL:** `http://localhost:8081/api/admin/users/{userId}/status?status=INACTIVE`
* **Example:** `http://localhost:8081/api/admin/users/3/status?status=INACTIVE`
* **Kafka Integration:**
  * Automatically publishes a **`UserDeactivatedEvent`** to Kafka topic: **`user-deactivated-topic`**.
  * Downstream services (like `forensic-vault-service`) receive this event and automatically cancel any pending lab reservations for that user.
* **Success Response (`200 OK`):** Returns user profile with `status: "INACTIVE"`.

#### D. Update User Role
* **Method:** `PATCH`
* **URL:** `http://localhost:8081/api/admin/users/{userId}/role?role=ROLE_ATTORNEY`
* **Example:** `http://localhost:8081/api/admin/users/4/role?role=ROLE_ATTORNEY`
* **Success Response (`200 OK`):** Returns user profile with updated role.

---

## ⚡ Architecture & Event Integration

```
[ Admin Deactivates User ]
            │
            ▼
   AdminUserService.java (auth-service)
            │
            ├─► PostgreSQL (`veritas_auth_db`): Sets status = INACTIVE
            │
            └─► Kafka Producer (Port 9092)
                     │
                     ▼
             Topic: `user-deactivated-topic`
                     │
                     ▼
         [ forensic-vault-service (Listener) ]
         Cancels active evidence reservations immediately!
```
