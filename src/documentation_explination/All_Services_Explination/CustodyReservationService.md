# CustodyReservationService Explanation

## What is CustodyReservationService?

In a forensic evidence vault, physical evidence (like a seized iPhone, laptop, or murder weapon) cannot simply sit on an open desk. 
Whenever a forensic examiner wants to inspect or run extraction tools on an evidence item, they must book a reserved time slot, check out the physical item to the lab, and check it back in under strict legal verification.

This service is the **heart of our Unit 3 Concurrency & Multithreading architecture**. It solves two critical real-world problems:
1. **The Concurrency Problem (Race Condition):** Stops two examiners from double-booking or checking out the exact same evidence at the exact same millisecond.
2. **The Multithreading Problem (Server Freezing):** Uses an `ExecutorService` thread pool to calculate the heavy SHA-256 cryptographic hash in the background upon check-in, verifying that no evidence was tampered with while in the lab.

---

## Variables Being Used and Their Reasons:

* **`reservationRepository`**: Talks to PostgreSQL (`custody_reservations` table). Saves bookings, finds overlapping reservation intervals, and checks examiner schedules.
* **`evidenceItemRepository`**: Performs queries and updates on the `evidence_items` table. Crucially provides `findByIdWithPessimisticLock()` for concurrency control.
* **`userRepository`**: Validates the examiner or administrator ID to make sure the user exists and has permission (`ROLE_FORENSIC_EXAMINER` or `ROLE_ADMIN`).
* **`custodyLogRepository`**: Automatically appends immutable audit events (`CHECK_OUT`, `CHECK_IN`) into the Chain of Custody ledger.
* **`labReportRepository`**: Saves the **10th table (`forensic_lab_reports`)**, recording the before-and-after hashes and thread telemetry.
* **`fileStorageService`**: Loads the physical file from the `uploads/` folder on disk so its raw byte stream can be read and hashed.
* **`notificationService`**: Broadcasts live real-time Server-Sent Events (SSE) to connected clients whenever an item is reserved, checked out, checked in, or quarantined.
* **`labVerificationExecutor`**: A fixed thread pool of 4 background worker threads (`Executors.newFixedThreadPool(4)`) with a custom `ThreadFactory` naming threads `lab-verifier-pool-1` to `4`.

---

## Methods and Their Implementation Explained:

### 1. `createReservation(request, examinerId)`: [The Concurrency Lock]
* Validates time boundaries (start time must be before end time, cannot be in the past).
* **The Pessimistic Lock:** Calls `evidenceItemRepository.findByIdWithPessimisticLock(id)`. This executes `SELECT ... FOR UPDATE` in PostgreSQL. If two examiners submit a booking request at the exact same millisecond, the database forces Examiner 2 to wait until Examiner 1 finishes.
* **Conflict Check:** Checks `findOverlappingReservations()`. If an existing booking already occupies that time interval, it throws `ConflictException` (**`409 Conflict`**).
* If available, saves the reservation in `CONFIRMED` status, sets evidence to `RESERVED`, and broadcasts `CUSTODY_RESERVATION_CREATED`.

### 2. `checkOutEvidence(reservationId, currentUserId)`
* Verifies ownership: strictly enforces that only the assigned examiner (or an Admin) can check out this reservation.
* Validates that the reservation status is `CONFIRMED`.
* Changes reservation status to `ACTIVE`.
* Changes evidence status to `IN_LAB`.
* Automatically logs a `CHECK_OUT` record in the Chain of Custody with the current hash.
* Broadcasts real-time event `EVIDENCE_CHECKED_OUT`.

### 3. `checkInWithLabReport(reservationId, request, currentUserId)`: [The Multithreading Check-In]
* Verifies examiner ownership and ensures the reservation is currently `ACTIVE`.
* Grabs the baseline pre-examination hash (`preExamHash`) stored when evidence was first ingested.
* **The Multithreaded Task:** Submits a `Callable<VerificationResult>` task to the `labVerificationExecutor` thread pool.
  * Worker thread (e.g. `lab-verifier-pool-1`) reads the file stream from disk.
  * Calculates a brand-new SHA-256 fingerprint (`ChecksumUtils.calculateSha256`).
  * Measures the duration in milliseconds and captures `Thread.currentThread().getName()`.
* **The Buzzer (`Future`):** Calls `future.get()` to safely receive the worker's result without freezing the main server.
* **Tamper Comparison:**
  * **If hashes match (`pre == post`):** Sets `integrityStatus = VERIFIED`, and evidence returns to `AVAILABLE`.
  * **If hashes mismatch (`pre != post`):** Tampering detected! Sets `integrityStatus = COMPROMISED`, and evidence is automatically locked as `SEQUESTERED` (quarantined).
* Saves the **10th table (`ForensicLabReport`)** with both hashes, the verdict, thread name, duration, and examiner findings.
* Logs `CHECK_IN` (or `INTEGRITY_FAILED`) in the Chain of Custody ledger.

### 4. `checkInEvidence(reservationId, notes, actionType, currentUserId)`
* Overloaded convenience method for standard check-ins, delegating to `checkInWithLabReport` while maintaining backward compatibility.

### 5. `cancelReservation(reservationId, currentUserId)`
* Allows the assigned examiner or Admin to cancel a pending or confirmed booking.
* Releases the evidence item back to `AVAILABLE` status.
* Appends an audit log in Chain of Custody noting that the reservation slot was released.

### 6. Query Methods (`getReservationById`, `getReservationsForEvidence`, `getReservationsForExaminer`)
* Retrieves paginated or list-based reservation metadata for evidence cards and examiner schedules.

### 7. `shutdownExecutor()`
* Annotated with `@PreDestroy`. When Spring Boot shuts down, it gracefully shuts down the thread pool, ensuring all active file verifications finish cleanly.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why Pessimistic Locking instead of Optimistic?** | In physical evidence lockers, two people cannot hold the same laptop. A pessimistic lock (`SELECT ... FOR UPDATE`) prevents conflicts before they happen at the database level. |
| **Why 4 Threads in the Thread Pool?** | Hashing multi-gigabyte files is CPU and disk-intensive. Setting a limit of 4 ensures background verifications never overload server CPU or starve web threads. |
| **What happens if someone tampers with the file?** | The worker calculates a mismatch, the status turns to `COMPROMISED`, evidence is quarantined as `SEQUESTERED`, and the 10th table preserves the digital proof for court. |
