# EvidenceService Explanation

## What is EvidenceService?

Digital and physical evidence is the cornerstone of criminal prosecution and civil litigation. Whether it is a surveillance camera MP4 video, a forensic phone extraction dump, a murder weapon photograph, or a signed financial contract, evidence must be handled under rigorous forensic protocols.

`EvidenceService` is the primary evidence intake, cataloging, and lifecycle management engine in VeritasVault. 

It fulfills four critical courtroom requirements:
1. **Cryptographic Baseline Fingerprinting:** The exact instant an evidence file is ingested, the service calculates an immutable SHA-256 cryptographic digest before the file is even written to disk.
2. **Automated Tracking Code Generation:** Assigns each evidence item a standardized, permanent courtroom barcode tracking number (e.g. `EV-2026-0001`).
3. **Automatic Chain of Custody Genesis:** Immediately upon creation, writes an initial `INGEST` audit block into the immutable Chain of Custody ledger with the baseline hash.
4. **Finite-State Machine (FSM) Lifecycle Enforcement:** Enforces strict legal rules preventing evidence from skipping procedural steps (e.g., an item cannot leap straight from `INGESTED` to `ADMITTED` in court without passing inspection).

---

## Variables Being Used and Their Reasons:

* **`evidenceItemRepository`**: Communicates with the `evidence_items` table in PostgreSQL. Performs CRUD operations, barcode lookups, case-filtered paginated searches, and provides pessimistic locking.
* **`legalCaseRepository`**: Validates that the legal case exists before associating new evidence with it.
* **`userRepository`**: Validates the identity and authorization of the uploader or police officer checking in the evidence.
* **`custodyLogRepository`**: Writes the genesis `INGEST` event and appends subsequent status transition audit logs directly into the immutable ledger.
* **`fileStorageService`**: Persists physical binary files to disk in organized case subdirectories (`uploads/evidence/case-X/`) with sanitized UUID filenames.

---

## Methods and Their Implementation Explained:

### 1. `uploadEvidence(MultipartFile file, EvidenceUploadRequest request, Long currentUserId)`: [Forensic Ingestion]
* **Validation:** Rejects empty or null uploads (`file.isEmpty()`) with `BadRequestException`.
* **Cryptographic Baseline Hashing:**
  * Opens the raw byte stream: `file.getInputStream()`.
  * Computes the SHA-256 digest via `ChecksumUtils.calculateSha256(...)`.
  * **Why now?** Calculating the hash before saving ensures that the checksum represents the exact, pristine state of the file as submitted by law enforcement.
* **Disk Storage Decoupling:**
  * Defines the case folder: `"evidence/case-" + legalCase.getId()`.
  * Calls `fileStorageService.storeFile(file, subfolder)`, returning a sanitized relative path (e.g., `evidence/case-4/9f2b-uuid.bin`).
* **Sequential Barcode Generation:**
  * Invokes the thread-safe `generateTrackingCode()`, yielding `EV-YYYY-XXXX`.
* **Evidence Item Creation:**
  * Populates metadata: seizing officer name, badge number, police agency, incident report number, source origin, file size, baseline hash, and sets initial status to `EvidenceStatus.INGESTED`.
  * Saves the `EvidenceItem` in PostgreSQL.
* **Chain of Custody Genesis Block:**
  * Automatically builds and saves the initial `ChainOfCustodyLog` event:
    * `actionType = CustodyActionType.INGEST`
    * `actor = uploader`
    * `hashAtEvent = sha256Checksum`
    * `notes = "Initial evidence intake, cryptographic hashing, and repository registration. Baseline SHA-256: " + sha256Checksum`
* **Return:** Maps and returns `EvidenceResponse` with original filename, MIME type, and tracking code.

### 2. `getEvidenceById(id)` & `getEvidenceByTrackingCode(trackingCode)`
* `getEvidenceById`: Fast lookup by numeric primary key.
* `getEvidenceByTrackingCode`: Lookup by formal courtroom tracking code (e.g., `EV-2026-0005`). Used when scanning physical barcodes in evidence lockers.

### 3. `getEvidenceByCaseId(...)`: [List & Paginated Filtered Views]
* Retrieves all evidence items filed under a specific court case.
* The paginated overload allows filtering evidence by `EvidenceStatus` (e.g. all `AVAILABLE` items) and `EvidenceType` (e.g. `DIGITAL_IMAGE`, `PHYSICAL_OBJECT`, `DOCUMENT`).

### 4. `loadEvidenceFile(Long id)`: [Evidence Streaming]
* Finds the evidence record, grabs its relative storage path, and delegates to `fileStorageService.loadAsResource(...)` to securely stream the binary file to authorized users.

### 5. `updateStatus(Long id, EvidenceStatus newStatus, String notes, Long currentUserId)`: [Lifecycle Transition]
* Loads the evidence item and current user.
* **FSM Validation:** Calls `isValidStatusTransition(previousStatus, newStatus)`. If an illegal leap is attempted, immediately aborts with `BadRequestException`.
* Updates `evidence.setStatus(newStatus)` and persists to PostgreSQL.
* Maps the status to an action type via `mapStatusToAction(newStatus)`.
* **Audit Trail Generation:** Automatically appends an immutable `ChainOfCustodyLog` capturing the status change, user notes, actor identity, and the item's cryptographic hash.

### 6. `isValidStatusTransition(EvidenceStatus from, EvidenceStatus to)`: [The Finite-State Machine]
* Enforces standard forensic evidence custody rules:
  * `INGESTED` ➔ Can only advance to `AVAILABLE` (after preliminary intake verification).
  * `AVAILABLE` ➔ Can transition to `RESERVED` (for lab checkout), `SEQUESTERED` (if flagged), `STAMPED` (for court redactions), or `ADMITTED` (submitted as trial exhibit).
  * `RESERVED` ➔ Can transition to `IN_LAB` (actively on examination bench) or back to `AVAILABLE` (if reservation cancelled).
  * `IN_LAB` ➔ Can return to `AVAILABLE` (checked back in verified) or `STAMPED`.
  * `STAMPED` ➔ Can transition to `AVAILABLE` or `ADMITTED`.
  * `SEQUESTERED` ➔ Can return to `AVAILABLE` (after investigative clearance).
  * `ADMITTED` ➔ **Terminal State.** Once evidence is officially admitted into court evidence by a judge, it is legally locked and cannot transition to any other status.

### 7. `generateTrackingCode()`: [Synchronized Barcode Generator]
* Declared as `private synchronized String generateTrackingCode()`.
* Combines the current year (`Year.now().getValue()`) with the formatted count:
  ```java
  return String.format("EV-%d-%04d", currentYear, count);
  ```
* Synchronized modifier prevents race conditions when concurrent uploads occur in the same millisecond.

### 8. `simulateTampering(Long evidenceId)`: [Unit 3 Lab Demo]
* Locates the evidence item and invokes `fileStorageService.appendTamperByte(...)`.
* Appends corrupted bytes to the physical file on disk, enabling real-time demonstration of the `CustodyReservationService` multithreaded tamper detection engine.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why calculate SHA-256 at upload rather than on-demand?** | Calculating at upload establishes the baseline "birth certificate" of the evidence. If you calculate it later, you cannot prove the file was not altered in the interim. |
| **Why is `generateTrackingCode` synchronized?** | If two police officers upload crime scene photos at the exact same millisecond, synchronization prevents both items from being assigned the identical tracking number `EV-2026-0001`. |
| **Why is `ADMITTED` a terminal state?** | Once evidence has been accepted by a judge and jury during trial, court rules prevent modifying its legal classification. It becomes part of the permanent trial record. |