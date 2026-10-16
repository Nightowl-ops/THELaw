# 04 - Chain of Custody & Cryptographic Audit Ledger

## 1. Functional Overview
The Chain of Custody module provides an append-only, tamper-evident audit ledger tracking every interaction, location transfer, analysis cycle, and status modification of digital evidence.

In legal proceedings, evidence is deemed inadmissible if its custodial timeline has unrecorded gaps. VeritasVault guarantees continuous custodial accounting: no entity record can be deleted, and every status change automatically captures the actor ID, cryptographic SHA-256 fingerprint, action type, timestamp, and qualitative notes.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.ChainOfCustodyLog`
    - `com.veritasvault.model.EvidenceItem`
    - `com.veritasvault.model.User`
    - `com.veritasvault.model.enums.CustodyActionType` (`POLICE_HANDOVER`, `INGEST`, `CHECK_OUT`, `ANALYZE`, `REDACT`, `CHECK_IN`, `COURT_SUBMISSION`)
- **Repositories:**
    - `com.veritasvault.repository.ChainOfCustodyLogRepository`
    - `com.veritasvault.repository.EvidenceItemRepository`
- **Services:**
    - `com.veritasvault.service.ChainOfCustodyService`
    - `com.veritasvault.service.EvidenceService` (generates logs on intake/status update)
    - `com.veritasvault.service.CustodyReservationService` (generates logs on check-out/check-in/cancel)
- **Controllers & DTOs:**
    - `com.veritasvault.controller.ChainOfCustodyController`
    - `com.veritasvault.dto.response.ChainOfCustodyResponse`

---

## 3. Workflow & Logic Sequence

### A. Automatic Log Generation Hooks
Custody records are generated automatically by internal services:
1. **Intake Event (`EvidenceService.uploadEvidence`):**
    - Automatically writes an entry with `CustodyActionType.INGEST`.
    - Records the baseline SHA-256 hash computed directly from the uploaded file stream.
2. **Lab Checkout (`CustodyReservationService.checkOutEvidence`):**
    - Automatically writes `CustodyActionType.CHECK_OUT`.
    - Records examiner ID, reservation reference ID, and current SHA-256 fingerprint.
3. **Lab Return / Check-in (`CustodyReservationService.checkInEvidence`):**
    - Automatically writes `CustodyActionType.CHECK_IN`.
    - Captures qualitative findings or notes entered by the examiner.
4. **Manual Status Transitions (`EvidenceService.updateStatus`):**
    - Maps `EvidenceStatus` values to corresponding `CustodyActionType` actions (e.g., `ADMITTED` -> `COURT_SUBMISSION`, `STAMPED` -> `REDACT`).
    - Appends a new timestamped log entry.

### B. Audit Trail Retrieval (`GET /api/custody/logs/evidence/{evidenceId}`)
1. Guarded by `@PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_ADMIN')")`.
2. Validates that the requested evidence ID exists via `EvidenceItemRepository.existsById()`. If absent, throws `ResourceNotFoundException`.
3. Queries `ChainOfCustodyLogRepository.findByEvidenceItemIdOrderByLoggedAtAsc(evidenceId)`.
4. Maps entities into a chronological audit response payload including actor contact info, timestamps, and recorded SHA-256 hashes.

---

## 4. API Endpoints

| HTTP Method | Endpoint | Required Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/custody/logs/evidence/{evidenceId}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Retrieves chronological chain-of-custody audit logs for an evidence item. |

---

## 5. Business Rules & Immutability Guarantees
- **Append-Only Persistence:** `ChainOfCustodyLog` contains no `UPDATE` or `DELETE` endpoints, service methods, or repository queries.
- **Creation Timestamp Locks:** The `loggedAt` field uses `@CreationTimestamp` with `updatable = false`.
- **Actor Accountability:** Null actors are prohibited; every log entry must link to an authenticated `User` account.