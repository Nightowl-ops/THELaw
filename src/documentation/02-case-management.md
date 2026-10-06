#02 - Legal Case Management

## 1. Functional Overview
The Case Management module serves as the primary relational root for all legal assets in VeritasVault. Evidence items, lab bookings, trial binders, and discovery packages are strictly associated with a specific legal case.

The system enforces strict role-based assignment: a case must have an attorney assigned (`ROLE_ATTORNEY` or `ROLE_ADMIN`) as the lead counsel, and can optionally link a validated client account (`ROLE_CLIENT`).

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.LegalCase`
    - `com.veritasvault.model.User`
    - `com.veritasvault.model.enums.CaseType` (`DEFENSE`, `PROSECUTION`)
    - `com.veritasvault.model.enums.CaseStatus` (`ACTIVE`, `PENDING_TRIAL`, `SETTLED`, `ARCHIVED`)
- **Repositories:**
    - `com.veritasvault.repository.LegalCaseRepository`
    - `com.veritasvault.repository.UserRepository`
- **Services:**
    - `com.veritasvault.service.LegalCaseService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.LegalCaseController`
    - `com.veritasvault.dto.request.CreateCaseRequest`
    - `com.veritasvault.dto.response.CaseResponse`

---

## 3. Workflow & Logic Sequence

### A. Case Registration (`POST /api/cases`)
1. Controller method is guarded by `@PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")`.
2. Validates incoming payload: checks for mandatory case number format, non-empty title, and valid `CaseType`.
3. Verifies case number uniqueness using `LegalCaseRepository.existsByCaseNumber()`. If duplicate, throws `BadRequestException`.
4. Fetches and validates lead attorney:
    - Must exist in database.
    - Must hold `ROLE_ATTORNEY` or `ROLE_ADMIN`. If not, throws `BadRequestException`.
5. If `clientId` is provided:
    - Must exist in database.
    - Must hold `ROLE_CLIENT`. If an attorney or examiner ID is passed as client, throws `BadRequestException`.
6. Sets initial status to `CaseStatus.ACTIVE`.
7. Persists entity and returns structured `CaseResponse`.

### B. Querying Cases
- **All Firm Cases:** `GET /api/cases` returns all cases (available to Admins, Attorneys, and Forensic Examiners).
- **Attorney Docket:** `GET /api/cases/attorney/{id}` filters cases by the lead attorney's ID.
- **Single Case Dossier:** `GET /api/cases/{id}` loads full case details, opposing party info, and counsel contacts.

---

## 4. API Endpoints

| HTTP Method | Endpoint | Required Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/cases` | `ROLE_ATTORNEY`, `ROLE_ADMIN` | Creates and registers a new legal case. |
| `GET` | `/api/cases` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Lists all active and archived firm cases. |
| `GET` | `/api/cases/{id}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_CLIENT`, `ROLE_ADMIN` | Fetches details for a specific case by ID. |
| `GET` | `/api/cases/attorney/{attorneyId}` | `ROLE_ATTORNEY`, `ROLE_ADMIN` | Retrieves all cases managed by a specific attorney. |

---

## 5. Business Rules & Edge Cases
- **Unique Case Numbering:** Case numbers (e.g., `CR-2026-0041`) must be unique across the entire database.
- **Strict Role Boundary:** A non-lawyer (e.g., a Forensic Examiner or Client) cannot be set as the `leadAttorney`. Attempting to do so triggers a `400 Bad Request`.
- **Relational Integrity:** Case deletion is disabled at the service level; cases transition through `CaseStatus` lifecycles (`ACTIVE` -> `PENDING_TRIAL` -> `SETTLED` -> `ARCHIVED`) to maintain audit trails.