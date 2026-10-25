# 08 - Unit 3 Concurrency, Thread Pooling & Tamper Verification

## 1. Functional Overview
The Unit 3 Concurrency and Multithreading module addresses two critical real-world forensic challenges in high-security evidence management:

1. **The Concurrency Collision Problem (Race Condition):**
   When multiple forensic examiners attempt to reserve the exact same evidence item for overlapping laboratory hours at the same millisecond, the database must prevent double-booking. VeritasVault enforces database-level pessimistic locking (`SELECT ... FOR UPDATE`), ensuring only one examiner acquires the reservation while concurrent collisions are rejected with HTTP `409 Conflict`.

2. **The Multithreaded Verification Problem (Server Responsiveness & Tamper Detection):**
   When an examiner returns physical evidence from the lab, the system must verify that the file has not been altered or corrupted. Calculating heavy SHA-256 cryptographic hashes on multi-gigabyte forensic images directly on web request threads would cause server CPU starvation and frozen connections. VeritasVault offloads hash calculation to a dedicated background `ExecutorService` thread pool. If the calculated hash differs from the baseline pre-examination hash, the evidence is automatically quarantined as `SEQUESTERED`.

---

## 2. Relevant Source Files
* **Model Entities & Enums:**
  * `com.veritasvault.model.ForensicLabReport` (10th Table)
  * `com.veritasvault.model.enums.LabIntegrityStatus` (`VERIFIED`, `COMPROMISED`)
  * `com.veritasvault.model.CustodyReservation`
  * `com.veritasvault.model.EvidenceItem`
* **Repositories:**
  * `com.veritasvault.repository.ForensicLabReportRepository`
  * `com.veritasvault.repository.CustodyReservationRepository`
  * `com.veritasvault.repository.EvidenceItemRepository` (contains `findByIdWithPessimisticLock`)
* **Services:**
  * `com.veritasvault.service.CustodyReservationService` (manages 4-worker `labVerificationExecutor`)
  * `com.veritasvault.service.NotificationService` (broadcasts live SSE telemetry)
  * `com.veritasvault.service.FileStorageService` (provides `appendTamperByte` simulation)
* **Controllers & DTOs:**
  * `com.veritasvault.controller.CustodyReservationController`
  * `com.veritasvault.dto.request.CreateLabReportRequest`
  * `com.veritasvault.dto.response.ForensicLabReportResponse`
* **Concurrency Verification Test:**
  * `com.veritasvault.service.CustodyReservationConcurrencyTest` (5-thread parallel `CountDownLatch` test suite)

---

## 3. Workflow & Logic Sequence

### A. Pessimistic Concurrency Reservation Flow
```
Examiner 1 (Worker-1) ──┐
Examiner 2 (Worker-2) ──┼──► [SELECT ... FOR UPDATE] ──► 1 Winner (201 Created)
Examiner 3 (Worker-3) ──┘    (Database Row Lock)     ──► 4 Collisions (409 Conflict)
```
1. Client requests booking: `POST /api/custody/reservations`.
2. Service executes `evidenceItemRepository.findByIdWithPessimisticLock(id)`. PostgreSQL places a lock on the evidence row.
3. Service checks `findOverlappingReservations()`.
4. If a conflict exists, throws `ConflictException` (HTTP `409 Conflict`).
5. If available, saves reservation as `CONFIRMED`, marks evidence as `RESERVED`, and broadcasts `CUSTODY_RESERVATION_CREATED` via SSE.

### B. Background Multithreaded Check-In Flow
```
Main HTTP Thread ──► Submit Callable Task ──► [lab-verifier-pool-1 to 4]
                                                       │
                                            (Background SHA-256 Hashing)
                                                       │
Main HTTP Thread ◄── Future.get() (Buzzer) ◄───────────┘
        │
        ├── If Hashes Match (VERIFIED)    ──► Evidence AVAILABLE
        └── If Hashes Differ (COMPROMISED) ──► Evidence SEQUESTERED (Quarantine)
```
1. Examiner submits check-in: `POST /api/custody/reservations/{id}/checkin`.
2. Service extracts initial baseline hash (`preExamHash`).
3. Submits a `Callable<VerificationResult>` task to `labVerificationExecutor` (4 fixed worker threads).
4. Background thread (e.g. `lab-verifier-pool-1`) reads the file stream from disk, computes a fresh SHA-256 hash, and measures duration in milliseconds.
5. The main thread calls `future.get()` to receive the outcome without freezing web throughput.
6. **Integrity Comparison:**
   * If `preExamHash.equals(postExamHash)`: Sets status to `VERIFIED`, returns evidence to `AVAILABLE`.
   * If `!preExamHash.equals(postExamHash)`: Sets status to `COMPROMISED`, locks evidence as `SEQUESTERED`.
7. Inserts the verification record into the **10th table (`forensic_lab_reports`)**.
8. Appends a `CHECK_IN` (or `INTEGRITY_FAILED`) log into the Chain of Custody ledger.
9. Broadcasts `EVIDENCE_CHECKED_IN` or `TAMPER_ALERT_QUARANTINE` across SSE telemetry.
