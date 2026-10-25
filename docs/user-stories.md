# VeritasVault - User Stories & Acceptance Criteria

This document details the functional user stories and acceptance criteria guiding the development of the VeritasVault Evidence Management platform.

---

### Story 1: Evidence Ingestion & Cryptographic Checksum (Law Enforcement / Investigator)
* **As a** Law Enforcement Officer or Detective,
* **I want to** upload and ingest evidence with automated SHA-256 cryptographic hashing,
* **So that** a digital baseline is established immediately at intake, proving the item was not tampered with prior to custody.

#### Acceptance Criteria:
1. System reads raw binary streams and computes a SHA-256 hash at upload.
2. An unalterable `EV-YYYY-XXXX` tracking barcode number is assigned sequentially.
3. System logs the initial `INGEST` block into the append-only Chain of Custody ledger.

---

### Story 2: Lab Custody Reservation & Concurrency Lock (Forensic Examiner)
* **As a** Forensic Examiner,
* **I want to** reserve a dedicated lab time slot for an evidence item,
* **So that** I can prevent double-booking and guarantee no other examiner analyzes the physical device at the same time.

#### Acceptance Criteria:
1. System checks existing bookings and rejects overlapping time slots with HTTP `409 Conflict`.
2. Employs database-level pessimistic locking (`SELECT ... FOR UPDATE`) to block race conditions when multiple examiners submit at the exact same millisecond.
3. System updates evidence status to `RESERVED` and logs `CHECK_OUT` when examination begins.

---

### Story 3: Background Multithreaded Check-In & Tamper Quarantine (Forensic Examiner)
* **As a** Forensic Examiner returning evidence from laboratory examination,
* **I want to** check in evidence with an automated background cryptographic verification,
* **So that** the server recalculates the hash without freezing web threads, and automatically quarantines tampered items.

#### Acceptance Criteria:
1. Check-in task delegates cryptographic hash calculation to a dedicated 4-thread `ExecutorService` pool (`Callable` + `Future`).
2. Records verification outcome in the 10th table (`forensic_lab_reports`) with pre-hash, post-hash, duration, and worker thread name.
3. If hashes match (`VERIFIED`), status flips to `AVAILABLE`.
4. If hashes mismatch (`COMPROMISED`), status flips to `SEQUESTERED` (quarantined) and an alert is broadcast via Server-Sent Events.

---

### Story 4: Trial Exhibit Binder & Continuous Bates Stamping (Attorney)
* **As a** Trial Attorney,
* **I want to** bundle evidence items into an official Exhibit Binder with sequential Bates page numbers,
* **So that** all exhibits are clearly indexed and legally ready for presentation to the judge and jury.

#### Acceptance Criteria:
1. System calculates consecutive Bates numbers automatically (e.g., `STATE-V-DOE-0001` to `STATE-V-DOE-0020`).
2. Removing an exhibit from the middle automatically triggers a re-indexing cascade without leaving gaps.
3. Once the binder is finalized or served, the system locks it against any further modifications.

---

### Story 5: Secure Discovery Production & Proof-of-Service (Opposing Counsel Link)
* **As an** Attorney,
* **I want to** generate a secure, expiring download link for an exhibit binder,
* **So that** I can legally share discovery documents with opposing counsel without having to create an internal login account for them.

#### Acceptance Criteria:
1. External counsel accesses the package using a high-entropy, expiring UUID token.
2. System stores a SHA-256 hash of the token in the database to prevent token leakage.
3. Every download event logs the recipient's IP address, browser User-Agent, and timestamp into `discovery_access_logs` as court-admissible Proof of Service.

---

### Story 6: Real-Time Lab Telemetry & Custody Alerts (System Users)
* **As a** Vault Administrator or Case Attorney,
* **I want to** receive instant real-time telemetry updates whenever evidence is reserved, checked out, or quarantined,
* **So that** I maintain complete situational awareness without constantly refreshing browser pages.

#### Acceptance Criteria:
1. Backend maintains persistent Server-Sent Events (SSE) connections with active frontend clients.
2. Uses thread-safe `CopyOnWriteArrayList<SseEmitter>` to safely broadcast events from background worker threads.
3. Automatically prunes disconnected clients to prevent memory leaks.
