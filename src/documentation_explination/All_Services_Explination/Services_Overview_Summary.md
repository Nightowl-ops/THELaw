# VeritasVault Backend Services: Architecture Overview Summary

This document provides a high-level architectural index and summary of all **11 core Spring Boot services** that power the VeritasVault Digital Chain of Custody & Legal Evidence Management platform.

For in-depth explanations covering every variable, database repository, method implementation, and courtroom rationale, refer to each service's individual markdown guide in this directory.

---

## Service Index & Core Responsibilities

```
                               ┌─────────────────────────────────┐
                               │           AuthService           │
                               │  (JWT Gateway & User Auth)      │
                               └──────────────┬──────────────────┘
                                              │
                   ┌──────────────────────────┼──────────────────────────┐
                   ▼                          ▼                          ▼
         ┌───────────────────┐      ┌───────────────────┐      ┌───────────────────┐
         │  AdminUserService │      │    EmailService   │      │NotificationService│
         │ (User Provision)  │      │  (SMTP Outbound)  │      │   (SSE Streams)   │
         └───────────────────┘      └───────────────────┘      └───────────────────┘
                   │
                   ▼
         ┌───────────────────┐
         │  LegalCaseService │
         │(Court Case Vault) │
         └─────────┬─────────┘
                   │
                   ▼
         ┌───────────────────┐                  ┌───────────────────────────────┐
         │  EvidenceService  │◄─────────────────┤ CustodyReservationService     │
         │ (Ingestion & Hash)│                  │ (Unit 3 Concurrency & Threads)│
         └─────────┬─────────┘                  └───────────────┬───────────────┘
                   │                                            │
                   ├────────────────────────────────────────────┤
                   ▼                                            ▼
         ┌───────────────────┐                  ┌───────────────────────────────┐
         │ChainOfCustodyServ.│                  │      FileStorageService       │
         │(Immutable Ledger) │                  │ (Disk Storage & Tamper Demo)  │
         └─────────┬─────────┘                  └───────────────────────────────┘
                   │
                   ▼
         ┌───────────────────┐
         │ExhibitBinderServ. │
         │(Bates Pagination) │
         └─────────┬─────────┘
                   │
                   ▼
         ┌───────────────────┐
         │DiscoveryProduction│
         │ (External Serving)│
         └───────────────────┘
```

---

### 1. [AuthService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/AuthService.md): *Identity & Access Gateway*
* **Core Role:** Serves as the security gatekeeper for VeritasVault, managing user registration, authentication, email verification, and profile management.
* **Key Architecture:**
  * **Role Restriction:** Public self-registration only grants `ROLE_CLIENT`. Elevated roles must be provisioned administratively.
  * **Stateless Security:** Issues cryptographically signed JSON Web Tokens (JWT) containing user claims and roles.
  * **Anti-Enumeration Defense:** Password reset endpoints return identical generic responses whether an email exists or not, preventing reconnaissance attacks.
  * **Disk Decoupling:** Uses `FileStorageService` to store user avatars on disk and persists clean path references in PostgreSQL.

---

### 2. [LegalCaseService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/LegalCaseService.md): *Courtroom Case Organizer*
* **Core Role:** Organizes evidence items, exhibits, and discovery productions under discrete court cases, lawsuits, or criminal indictments.
* **Key Architecture:**
  * **Unique Case Numbering:** Automatically generates standardized legal identifiers (`CV-2026-XXXX` or `CR-2026-XXXX`).
  * **Role Segregation:** Only attorneys and administrators can file new legal cases.
  * **Cascading Case Statistics:** Provides real-time counts of attached evidence items, binders, and assigned legal counsel.

---

### 3. [EvidenceService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/EvidenceService.md): *Forensic Ingestion & Integrity Engine*
* **Core Role:** Ingests physical and digital evidence into the vault, establishing an unalterable baseline cryptographic hash.
* **Key Architecture:**
  * **SHA-256 Hashing:** Computes cryptographic checksums at the exact millisecond of upload.
  * **Tracking Code Generator:** Generates unique barcode tracking codes (`EV-2026-XXXX`).
  * **Storage Decoupling:** Delegates binary persistence to `FileStorageService` while maintaining the database record.
  * **Automatic Genesis Block:** Immediately invokes `ChainOfCustodyService` upon creation to log the `INITIAL_INTAKE` ledger event.

---

### 4. [ChainOfCustodyService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/ChainOfCustodyService.md): *Immutable Court Audit Ledger*
* **Core Role:** Guarantees court admissibility under Federal Rule of Evidence 901 by maintaining an append-only, tamper-evident audit history of every evidence transfer.
* **Key Architecture:**
  * **Append-Only Immutability:** Contains zero `UPDATE` or `DELETE` endpoints; once an event is recorded, it can never be altered.
  * **Actor & Location Attribution:** Records who possessed the item, the legal reason, and the physical location.
  * **Integrity Hash Validation:** Records the file's SHA-256 hash at every touchpoint to verify no tampering occurred.

---

### 5. [CustodyReservationService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/CustodyReservationService.md): *Unit 3 Concurrency & Multithreaded Verification*
* **Core Role:** Prevents double-booking of physical evidence and performs high-speed background cryptographic hash verification when items return from forensic examination.
* **Key Architecture:**
  * **Pessimistic Concurrency Locking:** Uses database-level `SELECT ... FOR UPDATE` (`findByIdWithPessimisticLock`) to block simultaneous race conditions when multiple examiners attempt booking at the exact same millisecond.
  * **Multithreaded Thread Pool (`ExecutorService`):** Employs a dedicated 4-worker thread pool (`lab-verifier-pool-1` to `4`) running `Callable` tasks to calculate SHA-256 hashes in the background without freezing the web server.
  * **The 10th Table (`ForensicLabReport`):** Automatically logs before-and-after hashes, thread execution times, and worker thread telemetry.
  * **Tamper Quarantine:** If a hash mismatch occurs (`pre != post`), status flips to `COMPROMISED` and evidence is immediately locked as `SEQUESTERED`.

---

### 6. [ExhibitBinderService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/ExhibitBinderService.md): *Trial Binder & Bates Numbering Engine*
* **Core Role:** Curates trial exhibits into numbered courtroom binders and enforces sequential Bates numbering across exhibits.
* **Key Architecture:**
  * **Continuous Bates Pagination:** Automatically calculates starting and ending Bates ranges (`CASE-0001` to `CASE-0015`) based on exhibit page counts.
  * **Automatic Re-indexing:** Deleting an exhibit automatically triggers a recalculation loop that re-indexes all subsequent exhibits without leaving gaps.
  * **Trial Freeze Workflow:** Once a binder transitions from `DRAFT` to `FINALIZED` or `SERVED`, all modification attempts are strictly rejected.

---

### 7. [DiscoveryProductionService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/DiscoveryProductionService.md): *Legal Discovery & Proof-of-Service*
* **Core Role:** Securely serves finalized exhibit binders to external opposing counsel and generates legally admissible download receipts.
* **Key Architecture:**
  * **Ephemeral Cryptographic Tokens:** Generates time-limited, unguessable UUID access links that expire automatically.
  * **Token Hashing:** Stores SHA-256 hashes of tokens in PostgreSQL to prevent exposure if the database is dumped.
  * **Certified Delivery Receipts:** Automatically records the downloader's IP address, browser User-Agent, and exact timestamp into `discovery_access_logs` to provide courtroom proof of service.

---

### 8. [FileStorageService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/FileStorageService.md): *Filesystem Persistence & Security Layer*
* **Core Role:** Manages binary file I/O on the local filesystem, isolating heavy byte payloads from the relational database.
* **Key Architecture:**
  * **Path Traversal Defense:** Sanitizes filenames, blocks `..` escape sequences, and validates that all resolved paths stay inside the `uploads/` boundary.
  * **UUID Anonymization:** Replaces user-supplied filenames with unique UUIDs to prevent overwrite collisions and script execution exploits.
  * **Stream Copy:** Streams bytes directly to disk via input streams to minimize JVM memory overhead.
  * **Live Tamper Simulation (`appendTamperByte`):** Injects corrupting bytes into files for Unit 3 lab testing and tamper detection demonstrations.

---

### 9. [NotificationService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/NotificationService.md): *Real-Time Telemetry & SSE Streaming*
* **Core Role:** Broadcasts live server events to connected browser clients using Server-Sent Events (SSE).
* **Key Architecture:**
  * **Persistent Telemetry:** Maintains 30-minute keep-alive streams with frontend dashboards.
  * **Thread-Safe Concurrency:** Stores active client emitters in a `CopyOnWriteArrayList<SseEmitter>` to safely support concurrent broadcasts from background worker threads.
  * **Automatic Dead Connection Pruning:** Catches broken-pipe exceptions when users navigate away and purges disconnected emitters from memory.

---

### 10. [AdminUserService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/AdminUserService.md): *Administrative Governance & RBAC*
* **Core Role:** Empowers system administrators to manage user accounts, assign elevated roles, and enforce security policies.
* **Key Architecture:**
  * **Privilege Elevation:** Provisions high-security roles (`ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN`) that are blocked from public self-registration.
  * **Instant Verification:** Automatically activates administrator-provisioned users without requiring an external email verification loop.
  * **Account Suspension:** Allows administrators to instantly suspend compromised or departed users, immediately terminating login access.

---

### 11. [EmailService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/EmailService.md): *Outbound SMTP Communications*
* **Core Role:** Transmits transactional emails containing account verification links and password recovery tokens.
* **Key Architecture:**
  * **Plain-Text Deliverability:** Uses Spring's `SimpleMailMessage` to maximize inbox deliverability and bypass aggressive spam quarantine filters.
  * **Failure Isolation:** Catches transport exceptions gracefully so that SMTP relay timeouts do not crash ongoing database transactions.

---

## Service Summary Matrix

| Service | Primary Database Entity | Concurrency / Security Pattern | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **`AuthService`** | `users` | Stateless JWT, BCrypt, 24-hr token | `UserRepository`, `PasswordEncoder`, `JwtTokenProvider`, `EmailService` |
| **`LegalCaseService`** | `legal_cases` | Case uniqueness, RBAC enforcement | `LegalCaseRepository`, `UserRepository` |
| **`EvidenceService`** | `evidence_items` | SHA-256 intake checksum | `EvidenceItemRepository`, `FileStorageService`, `ChainOfCustodyService` |
| **`ChainOfCustodyService`** | `chain_of_custody_logs` | Append-only immutability | `ChainOfCustodyLogRepository`, `EvidenceItemRepository` |
| **`CustodyReservationService`** | `custody_reservations`, `forensic_lab_reports` | **Pessimistic DB lock (`FOR UPDATE`), 4-thread `ExecutorService`** | `CustodyReservationRepository`, `ForensicLabReportRepository`, `NotificationService` |
| **`ExhibitBinderService`** | `exhibit_binders`, `binder_exhibits` | Sequential Bates numbering, status freeze | `ExhibitBinderRepository`, `BinderExhibitRepository` |
| **`DiscoveryProductionService`** | `discovery_productions`, `discovery_access_logs` | SHA-256 token hashing, IP/User-Agent receipts | `DiscoveryProductionRepository`, `DiscoveryAccessLogRepository`, `FileStorageService` |
| **`FileStorageService`** | Filesystem (`uploads/`) | Path traversal guard, UUID naming | `Path rootStorageLocation` |
| **`NotificationService`** | Memory (`List<SseEmitter>`) | **`CopyOnWriteArrayList` thread safety, SSE streaming** | `ObjectMapper` |
| **`AdminUserService`** | `users` | Administrative RBAC gatekeeping | `UserRepository`, `PasswordEncoder`, `AuthService` |
| **`EmailService`** | SMTP Relay | Resilient exception handling | `JavaMailSender` |