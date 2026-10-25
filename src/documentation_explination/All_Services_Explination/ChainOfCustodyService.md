# ChainOfCustodyService Explanation

## What is ChainOfCustodyService?

In legal jurisprudence, the **Chain of Custody** is the single most critical evidentiary document in any criminal or civil trial. Under **Federal Rule of Evidence 901** (Authenticating or Identifying Evidence), the prosecution or plaintiff must prove beyond a reasonable doubt that the evidence presented in the courtroom is bit-for-bit identical to what was seized at the crime scene.

### The Real-World Lesson: The O.J. Simpson Trial
In the 1995 O.J. Simpson murder trial, a police detective carried a reference vial of the defendant's blood in his pocket for several hours instead of immediately booking it into the secured police evidence vault. 
That unaccounted-for gap in time created reasonable doubt in the minds of the jury, leading the defense to successfully argue that evidence could have been planted or contaminated. The entire physical case collapsed.

### The Architectural Philosophy of ChainOfCustodyService
In VeritasVault, the Chain of Custody is an **Append-Only Immutable Ledger**. 
Whenever an evidence item is:
1. **Initially Ingested** (`INGEST`)
2. **Reserved & Checked Out to the Lab** (`CHECK_OUT`)
3. **Returned to the Safe with Hash Verification** (`CHECK_IN`)
4. **Redacted or Stamped** (`REDACT`)
5. **Formally Submitted to the Court** (`COURT_SUBMISSION`)

A permanent, tamper-evident audit record is stamped recording **who** touched it, **what** exact millisecond they touched it, **why** they touched it, and **what the SHA-256 cryptographic hash was at that exact moment**.

---

## What is Missing from this Service (By Deliberate Design):

If you examine `ChainOfCustodyService.java`, you will notice something extraordinary:
* **There is NO `updateLog()` method.**
* **There is NO `deleteLog()` method.**
* **There is NO `POST /api/custody` endpoint.**

In court evidentiary standards, **editing or deleting a chain of custody log is illegal evidence tampering**. Furthermore, users cannot manually type up a "fake" log entry. Writes happen exclusively through verified internal system transactions inside [EvidenceService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/EvidenceService.md) and [CustodyReservationService](file:///C:/Users/USER/Downloads/Law/src/documentation_explination/All_Services_Explination/CustodyReservationService.md).

---

## Variables Being Used and Their Reasons:

* **`custodyLogRepository`**: Interfaces with the `chain_of_custody_logs` table in PostgreSQL. Retrieves the chronological sequence of events for a specific evidence item.
* **`evidenceItemRepository`**: Verifies that the evidence item actually exists in the database before attempting to retrieve its audit trail, preventing invalid query errors.

---

## Methods and Their Implementation Explained:

### 1. `getAuditTrailForEvidence(Long evidenceId)`: [The Courtroom Timeline]
* Annotated with `@Transactional(readOnly = true)`.
* **Existence Check:**
  * Calls `evidenceItemRepository.existsById(evidenceId)`.
  * If the item is not found, throws `ResourceNotFoundException` ("Evidence item not found with ID: " + evidenceId).
* **Strict Chronological Retrieval:**
  * Executes `custodyLogRepository.findByEvidenceItemIdOrderByLoggedAtAsc(evidenceId)`.
  * **Why `OrderByLoggedAtAsc`?** Evidentiary law requires the history of custody to be presented in chronological sequence from genesis upload through every lab inspection to the courtroom door.
* **Transformation:**
  * Streams the resulting entities through `this::mapToResponse`.
  * Collects into an immutable `List<ChainOfCustodyResponse>`.

### 2. `mapToResponse(ChainOfCustodyLog log)`: [DTO Mapper]
* Maps the internal `ChainOfCustodyLog` entity into a sanitized `ChainOfCustodyResponse` DTO.
* Extracts the relevant audit details:
  * `id`: Unique numeric primary key of the log entry.
  * `evidenceId` & `evidenceTrackingCode`: Barcode tracking code (e.g., `EV-2026-0001`).
  * `evidenceName`: Descriptive item title.
  * `actorId`, `actorName`, `actorEmail`: Identity of the law enforcement officer, attorney, or examiner who performed the action.
  * `actionType`: Formal legal event type (`INGEST`, `CHECK_OUT`, `CHECK_IN`, `COURT_SUBMISSION`, `ANALYZE`, `REDACT`).
  * `notes`: Mandatory forensic notes explaining the operational reason for the custody transfer.
  * `hashAtEvent`: The SHA-256 cryptographic checksum captured at the time of the event.
  * `loggedAt`: The immutable database timestamp of the transaction.

---

## How Writes Occur in the Ledger:

Since `ChainOfCustodyService` is strictly read-only, where do new chain of custody blocks come from?

```
                      ┌────────────────────────────────────────┐
                      │ Evidence Ingest / Status Update Flow  │
                      └──────────────────┬─────────────────────┘
                                         │
                                         ▼
                               ┌───────────────────┐
                               │  EvidenceService  │
                               └─────────┬─────────┘
                                         │  (Saves INGEST or Status Log)
                                         ▼
                      ┌────────────────────────────────────────┐
                      │        chain_of_custody_logs           │
                      │       (PostgreSQL Append-Only)         │
                      └──────────────────▲─────────────────────┘
                                         │  (Saves CHECK_OUT or CHECK_IN Log)
                               ┌─────────┴─────────┐
                               │CustodyReservation │
                               │     Service       │
                               └───────────────────┘
                                         ▲
                                         │
                      ┌──────────────────┴─────────────────────┐
                      │  Lab Checkout / Multithreaded Checkin  │
                      └────────────────────────────────────────┘
```

1. **At Ingestion:** `EvidenceService.uploadEvidence` calculates the initial SHA-256 checksum, saves the evidence, and immediately inserts the genesis `INGEST` log entry.
2. **At Status Updates:** `EvidenceService.updateStatus` verifies the legal finite-state machine and writes an audit event detailing the new legal state.
3. **At Lab Checkout:** `CustodyReservationService.checkOutEvidence` creates a `CHECK_OUT` record stamping the examiner taking possession.
4. **At Lab Return:** `CustodyReservationService.checkInWithLabReport` runs background SHA-256 recalculation via its 4-thread `ExecutorService` and appends a `CHECK_IN` (or `INTEGRITY_FAILED`) log containing the verified hash.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why is this service read-only?** | In real-world evidence auditing, allowing users to manually create, edit, or delete custody logs would invalidate the entire system in a court of law. Only system-verified lifecycle events are permitted to write records. |
| **Why is `hashAtEvent` recorded on every single log?** | Stamping the SHA-256 hash at every event provides mathematical proof that the evidence was not altered, substituted, or corrupted between intake, lab examination, and trial. |
| **Why `OrderByLoggedAtAsc` instead of Descending?** | Judges and juries need to follow the chronological chain of events from start to finish. Oldest-first ensures the timeline reflects the unbroken historical narrative. |
