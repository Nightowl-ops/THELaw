# 03 - Evidence Intake, Storage & Cryptographic Hashing

## 1. Functional Overview
The Evidence Ingestion module handles the upload, cryptographic integrity calculation, physical disk allocation, and tracking of digital forensics assets.

Every ingested evidence item is fingerprinted using a streaming SHA-256 algorithm before writing to disk. The computed digest acts as the baseline hash against which all future access, lab work, and court presentations are compared. Path traversal guards prevent filesystem vulnerabilities, and the intake event automatically triggers an initial record in the Chain of Custody ledger.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.EvidenceItem`
    - `com.veritasvault.model.enums.EvidenceType` (`VIDEO`, `AUDIO`, `DOCUMENT`, `DEVICE_IMAGE`, `NETWORK_LOG`)
    - `com.veritasvault.model.enums.EvidenceStatus` (`INGESTED`, `AVAILABLE`, `RESERVED`, `IN_LAB`, `STAMPED`, `ADMITTED`, `SEQUESTERED`)
- **Utilities & Storage:**
    - `com.veritasvault.util.ChecksumUtils`
    - `com.veritasvault.service.FileStorageService`
- **Repositories:**
    - `com.veritasvault.repository.EvidenceItemRepository`
    - `com.veritasvault.repository.ChainOfCustodyLogRepository`
- **Services:**
    - `com.veritasvault.service.EvidenceService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.EvidenceController`
    - `com.veritasvault.dto.request.EvidenceUploadRequest`
    - `com.veritasvault.dto.response.EvidenceResponse`

---

## 3. Workflow & Logic Sequence

### A. Multipart Upload & Intake (`POST /api/evidence/upload`)
1. Endpoint consumes `multipart/form-data` containing two parts:
    - `file`: Raw binary upload.
    - `data`: JSON metadata payload (`EvidenceUploadRequest`).
2. Checks that file is non-null and not empty.
3. **Cryptographic Checksum Calculation:**
    - Reads `file.getInputStream()` via `ChecksumUtils.calculateSha256()`.
    - Uses an 8 KB buffer chunking pattern into `java.security.MessageDigest` to avoid out-of-memory errors on large forensic images (e.g., Cellebrite extractions).
    - Generates a 64-character lowercase hex digest string.
4. **Secure File Storage:**
    - `FileStorageService.storeFile()` cleans path characters and verifies no directory traversal (`..`) sequences exist.
    - Generates a UUID filename preserving original extension.
    - Resolves target directory: `uploads/evidence/case-{caseId}/`.
    - Asserts that target path remains inside root directory bounds.
    - Saves file using `Files.copy(..., StandardCopyOption.REPLACE_EXISTING)`.
5. **Tracking Code Generation:**
    - Invokes `generateTrackingCode()` to produce an identifier in the format `EV-YYYY-XXXX` (e.g., `EV-2026-0002`).
6. **Persistence & Initial Ledger Entry:**
    - Persists `EvidenceItem` with status `EvidenceStatus.INGESTED`.
    - Automatically writes an immutable `ChainOfCustodyLog` entry with `CustodyActionType.INGEST`, storing the baseline hash.

### B. Secure Evidence Download (`GET /api/evidence/{id}/download`)
1. Verifies caller authorization (`ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN`).
2. Resolves relative path from entity (`storageUrl`).
3. Ensures resource exists and is readable on disk.
4. Returns an `octet-stream` response with original filename headers.

---

## 4. API Endpoints

| HTTP Method | Endpoint | Required Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/evidence/upload` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Ingests file, computes SHA-256, stores asset, creates custody record. |
| `GET` | `/api/evidence/{id}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Fetches evidence metadata by internal ID. |
| `GET` | `/api/evidence/tracking/{code}`| `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Fetches evidence details by court tracking code (e.g., `EV-2026-0001`). |
| `GET` | `/api/evidence/case/{caseId}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Lists all evidence assigned to a specific case. |
| `GET` | `/api/evidence/{id}/download` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Streams physical binary file from disk. |
| `PATCH` | `/api/evidence/{id}/status` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Updates operational status (e.g., SEQUESTERED, ADMITTED). |

---

## 5. Business Rules & Edge Cases
- **Stream-Based Hashing:** Hashing processes chunks rather than reading entire byte arrays into RAM, allowing large files to process safely without `OutOfMemoryError`.
- **Path Traversal Protection:** Any filename containing `..` or attempting to escape the storage root throws `BadRequestException`.
- **Zero Evidence Overwrites:** When files are saved, `UUID.randomUUID()` generates unique filenames on disk to eliminate race-condition overwrites.