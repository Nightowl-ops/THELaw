# VeritasVault - Digital Evidence Vault & Chain of Custody Management System

VeritasVault is an enterprise-grade digital and physical evidence management system engineered for law firms, district attorney offices, and forensic laboratories. It enforces strict compliance with **Federal Rule of Evidence 901** by maintaining an immutable, append-only chain of custody ledger and applying automated cryptographic verification across the evidentiary lifecycle.

---

## Complete Database Schema (10 Tables)

### 1. `users`
* `id`: Primary Key (BigInt, Auto-Increment)
* `full_name`: Varchar(150), Not Null
* `email`: Varchar(100), Unique, Not Null
* `password_hash`: Varchar(255), Not Null
* `role`: Enum (`ROLE_ADMIN`, `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_CLIENT`)
* `status`: Enum (`ACTIVE`, `INACTIVE`, `SUSPENDED`)
* `profile_picture_url`: Varchar(255)
* `is_email_verified`: Boolean, Default False
* `verification_token`: Varchar(255)
* `verification_token_expiry`: Timestamp
* `password_reset_token`: Varchar(255)
* `password_reset_token_expiry`: Timestamp
* `organization_or_agency`: Varchar(150)
* `bar_or_badge_number`: Varchar(50)
* `phone_number`: Varchar(30)
* `created_at` / `updated_at`: Timestamp

### 2. `legal_cases`
* `id`: Primary Key (BigInt, Auto-Increment)
* `case_number`: Varchar(50), Unique, Not Null (e.g., `CV-2026-0042`)
* `case_title`: Varchar(255), Not Null
* `case_type`: Enum (`CRIMINAL`, `CIVIL`, `CORPORATE`, `FAMILY`, `PROBATE`)
* `lead_attorney_id`: Foreign Key (`users.id`), Not Null
* `client_id`: Foreign Key (`users.id`), Nullable
* `opposing_party_name`: Varchar(150)
* `opposing_counsel_name`: Varchar(150)
* `opposing_counsel_email`: Varchar(100)
* `status`: Enum (`ACTIVE`, `PENDING_TRIAL`, `CLOSED`, `ARCHIVED`)
* `created_at` / `updated_at`: Timestamp

### 3. `evidence_items`
* `id`: Primary Key (BigInt, Auto-Increment)
* `case_id`: Foreign Key (`legal_cases.id`), Not Null
* `item_tracking_code`: Varchar(50), Unique, Not Null (e.g., `EV-2026-0001`)
* `name`: Varchar(200), Not Null
* `description`: Text
* `evidence_type`: Enum (`DIGITAL_IMAGE`, `DIGITAL_VIDEO`, `DIGITAL_DOCUMENT`, `PHYSICAL_OBJECT`, `FORENSIC_EXTRACTION`)
* `source_origin`: Varchar(200)
* `seizing_officer_name`: Varchar(150)
* `officer_badge_number`: Varchar(50)
* `law_enforcement_agency`: Varchar(150)
* `police_incident_report_num`: Varchar(100)
* `storage_url`: Varchar(255), Not Null
* `file_size_bytes`: BigInt
* `sha256_hash`: Varchar(64), Not Null
* `status`: Enum (`INGESTED`, `AVAILABLE`, `RESERVED`, `IN_LAB`, `STAMPED`, `SEQUESTERED`, `ADMITTED`)
* `uploaded_by_user_id`: Foreign Key (`users.id`), Not Null
* `created_at` / `updated_at`: Timestamp

### 4. `chain_of_custody_logs`
* `id`: Primary Key (BigInt, Auto-Increment)
* `evidence_id`: Foreign Key (`evidence_items.id`), Not Null
* `actor_user_id`: Foreign Key (`users.id`), Not Null
* `action_type`: Enum (`INGEST`, `CHECK_OUT`, `CHECK_IN`, `COURT_SUBMISSION`, `ANALYZE`, `REDACT`)
* `notes`: Text
* `hash_at_event`: Varchar(64), Not Null
* `logged_at`: Timestamp, Not Null

### 5. `custody_reservations`
* `id`: Primary Key (BigInt, Auto-Increment)
* `evidence_id`: Foreign Key (`evidence_items.id`), Not Null
* `examiner_id`: Foreign Key (`users.id`), Not Null
* `start_time`: Timestamp, Not Null
* `end_time`: Timestamp, Not Null
* `status`: Enum (`PENDING`, `CONFIRMED`, `ACTIVE`, `COMPLETED`, `CANCELLED`)
* `notes`: Text
* `created_at` / `updated_at`: Timestamp

### 6. `forensic_lab_reports` *(10th Table - Unit 3 Multithreading & Verification)*
* `id`: Primary Key (BigInt, Auto-Increment)
* `reservation_id`: Foreign Key (`custody_reservations.id`), Unique, Not Null
* `evidence_id`: Foreign Key (`evidence_items.id`), Not Null
* `examiner_id`: Foreign Key (`users.id`), Not Null
* `pre_exam_hash`: Varchar(64), Not Null
* `post_exam_hash`: Varchar(64), Not Null
* `integrity_status`: Enum (`VERIFIED`, `COMPROMISED`)
* `thread_name`: Varchar(100), Not Null (e.g., `lab-verifier-pool-1`)
* `verification_duration_ms`: BigInt, Not Null
* `findings_summary`: Text
* `verified_at`: Timestamp, Not Null

### 7. `exhibit_binders`
* `id`: Primary Key (BigInt, Auto-Increment)
* `case_id`: Foreign Key (`legal_cases.id`), Not Null
* `binder_name`: Varchar(150), Not Null
* `bates_prefix`: Varchar(30), Not Null (e.g., `STATE-V-DOE`)
* `status`: Enum (`DRAFT`, `FINALIZED`, `SERVED`)
* `created_by_user_id`: Foreign Key (`users.id`), Not Null
* `created_at` / `updated_at`: Timestamp

### 8. `binder_exhibits`
* `id`: Primary Key (BigInt, Auto-Increment)
* `binder_id`: Foreign Key (`exhibit_binders.id`), Not Null
* `evidence_id`: Foreign Key (`evidence_items.id`), Not Null
* `exhibit_number`: Varchar(30), Not Null (e.g., `Exhibit A`)
* `bates_start_num`: Integer, Not Null
* `bates_end_num`: Integer, Not Null
* `added_at`: Timestamp, Not Null

### 9. `discovery_productions`
* `id`: Primary Key (BigInt, Auto-Increment)
* `case_id`: Foreign Key (`legal_cases.id`), Not Null
* `binder_id`: Foreign Key (`exhibit_binders.id`), Not Null
* `target_party_name`: Varchar(150), Not Null
* `access_token`: Varchar(255), Not Null
* `access_token_hash`: Varchar(64), Not Null
* `is_watermarked`: Boolean, Default True
* `expires_at`: Timestamp, Not Null
* `created_by_user_id`: Foreign Key (`users.id`), Not Null
* `created_at`: Timestamp

### 10. `discovery_access_logs`
* `id`: Primary Key (BigInt, Auto-Increment)
* `production_id`: Foreign Key (`discovery_productions.id`), Not Null
* `ip_address`: Varchar(50), Not Null
* `user_agent`: Varchar(255), Not Null
* `downloaded_file_name`: Varchar(200), Not Null
* `accessed_at`: Timestamp, Not Null

---

## Entity Relationships & Architectural Principles

1. **`users` ➔ `legal_cases` (One-to-Many):** One lead attorney manages multiple court cases; one client is associated with their own case docket. Client-scoped queries guarantee strict attorney-client data isolation.
2. **`legal_cases` ➔ `evidence_items` (One-to-Many):** Evidence is strictly anchored to its parent case docket. Cross-case evidence contamination is structurally prevented.
3. **`evidence_items` ➔ `chain_of_custody_logs` (One-to-Many):** Every evidence item maintains an unbroken, append-only chronological ledger of every handler, action, and cryptographic SHA-256 hash.
4. **`evidence_items` ➔ `custody_reservations` (One-to-Many):** Examiners reserve time intervals for forensic examination. Protected by pessimistic database locks (`SELECT ... FOR UPDATE`) to block simultaneous booking race conditions.
5. **`custody_reservations` ➔ `forensic_lab_reports` (One-to-One):** Check-in dispatches a background task to a dedicated 4-worker thread pool (`ExecutorService`). Before-and-after hashes are compared; tampered evidence is quarantined as `SEQUESTERED`.
6. **`legal_cases` ➔ `exhibit_binders` (One-to-Many):** A case can contain multiple trial binders (e.g., Bail Hearing, Motion to Suppress, Trial).
7. **`exhibit_binders` ➔ `evidence_items` via `binder_exhibits` (Many-to-Many):** Enforces continuous Bates page numbering across exhibits with automatic re-indexing when exhibits are removed.
8. **`exhibit_binders` ➔ `discovery_productions` (One-to-Many):** Finalized binders are served to external opposing parties via secure, time-expiring UUID download tokens.
9. **`discovery_productions` ➔ `discovery_access_logs` (One-to-Many):** Every download logs IP address and browser User-Agent, providing legally admissible proof of service.

---

## Security Model & Soft Deletion

* **Role-Based Access Control (RBAC):**
  * `ROLE_ADMIN`: Complete vault governance, user creation, and status management.
  * `ROLE_ATTORNEY`: Case creation, exhibit binder curation, and discovery production.
  * `ROLE_FORENSIC_EXAMINER`: Lab reservations, evidence checkout, and forensic check-in verification.
  * `ROLE_CLIENT`: Read-only access restricted strictly to their assigned legal cases.
* **Soft Deletions:**
  * User accounts are never purged from PostgreSQL because historic chain-of-custody logs require persistent foreign keys to identify past handlers. Deactivation sets `status = INACTIVE` or `SUSPENDED`, terminating login capabilities while preserving evidentiary provenance.

---

## Application Profiles & Configuration

* **`application.yml`**: Master configuration setting active profile, port (`8085`), storage paths (`uploads/`), and JWT secret keys.
* **`application-dev.yml`**: Local development profile configured for PostgreSQL database persistence and SMTP relays.
* **`application-test.yml`**: In-memory H2 database profile with isolated file systems for fast, reproducible unit and concurrency testing.
