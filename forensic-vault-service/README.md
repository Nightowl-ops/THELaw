# Forensic Vault & Custody Service (`forensic-vault-service`)

**Port:** `8083`  
**Database:** Single Shared PostgreSQL (`veritas_db`)  
**Shared Event Bus:** Apache Kafka (KRaft mode)

---

## 1. Responsibilities

The `forensic-vault-service` is the cryptographic and custodial core of VeritasVault, responsible for:
1. **Lab Bench Reservations:** Booking lab slots for evidence examination with SQL pessimistic locks and interval collision checks to prevent double-booking.
2. **Chain of Custody Ledger:** Immutable, chronological audit logs tracking every actor, action (`INGEST`, `CHECK_OUT`, `CHECK_IN`, `ANALYZE`), and SHA-256 fingerprint state.
3. **Multithreaded Check-In:** Fixed 4-worker thread pool (`lab-verifier-pool`) executing parallel cryptographic SHA-256 recalculation against physical files on disk.
4. **Tamper Detection & Quarantine:** If post-examination SHA-256 diverges from baseline pre-examination hash, the service flags `COMPROMISED`, emits a Kafka `evidence-tampered-topic` event to trigger automated distributed quarantine in Service 2, and broadcasts an alert.
5. **Batch Case Integrity Audit:** Concurrently auditing all evidence files belonging to a legal case using `invokeAll()` across the 4-worker thread pool.
6. **Real-Time Telemetry Stream (SSE):** Persistent Server-Sent Events stream (`/api/notifications/stream`) delivering live telemetry events to the React frontend.

---

## 2. API Endpoints

### Custody Reservations & Check-Out / Check-In (`/api/custody/reservations`)
| Method | Endpoint | Description | Permitted Roles |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/custody/reservations` | Book lab custody reservation | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `PATCH` | `/api/custody/reservations/{id}/checkout` | Check out evidence to `IN_LAB` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `PATCH` | `/api/custody/reservations/{id}/checkin` | Check in evidence (notes only) | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `POST` | `/api/custody/reservations/{id}/checkin` | Multithreaded check-in with Lab Report | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `PATCH` | `/api/custody/reservations/{id}/cancel` | Cancel reservation & release slot | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `GET` | `/api/custody/reservations/{id}` | Get reservation by ID | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `GET` | `/api/custody/reservations/{id}/report` | Get forensic lab report | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `GET` | `/api/custody/reservations/evidence/{evidenceId}` | List reservations for evidence | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `GET` | `/api/custody/reservations/examiner/{examinerId}` | List reservations by examiner | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `POST` | `/api/custody/reservations/case/{caseId}/audit` | Run parallel batch integrity audit | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |

### Chain of Custody Ledger (`/api/custody/logs`)
| Method | Endpoint | Description | Permitted Roles |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/custody/logs/evidence/{evidenceId}` | Get chronological custodial ledger | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |

### Real-Time SSE Telemetry Stream (`/api/notifications`)
| Method | Endpoint | Description | Permitted Roles |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications/stream` | Server-Sent Events stream (`?token=...`) | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |

---

## 3. Kafka Topics & Events

| Direction | Topic | Payload | Purpose |
| :--- | :--- | :--- | :--- |
| **Producer** | `evidence-tampered-topic` | `EvidenceTamperedEvent` | Notifies `case-evidence-service` to immediately mark evidence as `SEQUESTERED` |
| **Consumer** | `evidence-ingested-topic` | `EvidenceIngestedEvent` | Automatically creates initial intake record in `chain_of_custody_logs` |
| **Consumer** | `user-deactivated-topic` | `UserDeactivatedEvent` | Automatically cancels active/pending reservations for deactivated examiners |

---

## 4. Multithreading Architecture

- **Thread Pool:** `labVerificationExecutor = Executors.newFixedThreadPool(4)`
- **Thread Naming:** `lab-verifier-pool-1` ... `lab-verifier-pool-4` (daemon threads)
- **Check-In Verification:** Executes hash computation as an asynchronous `Callable<VerificationResult>` submitted to the pool.
- **Batch Case Audit:** Concurrently calculates hashes across all case evidence using `labVerificationExecutor.invokeAll(tasks)`.
