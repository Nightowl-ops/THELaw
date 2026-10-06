# 06 - Exhibit Binders & Consecutive Bates Stamping

## 1. Functional Overview
The Exhibit Binder module organizes disparate case evidence into formal trial binders and assigns consecutive legal Bates numbering ranges across all exhibits.

Bates numbering assigns persistent, sequential identifier codes to every page of legal discovery packages (e.g., `DEF-0001` through `DEF-0045`). This module calculates offsets dynamically, prevents duplicate exhibits, and enforces an immutability lock once a binder is `FINALIZED` or `SERVED`.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.ExhibitBinder`
    - `com.veritasvault.model.BinderExhibit`
    - `com.veritasvault.model.EvidenceItem`
    - `com.veritasvault.model.LegalCase`
    - `com.veritasvault.model.enums.BinderStatus` (`DRAFT`, `UNDER_REVIEW`, `FINALIZED`, `SERVED`)
- **Repositories:**
    - `com.veritasvault.repository.ExhibitBinderRepository`
    - `com.veritasvault.repository.BinderExhibitRepository`
    - `com.veritasvault.repository.EvidenceItemRepository`
    - `com.veritasvault.repository.LegalCaseRepository`
- **Services:**
    - `com.veritasvault.service.ExhibitBinderService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.ExhibitBinderController`
    - `com.veritasvault.dto.request.CreateBinderRequest`
    - `com.veritasvault.dto.request.AddExhibitRequest`
    - `com.veritasvault.dto.response.BinderResponse`

---

## 3. Workflow & Logic Sequence

### A. Binder Initialization (`POST /api/binders`)
1. Guarded by `@PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")`.
2. Validates `batesPrefix` format against regex `^[A-Z0-9_-]{2,12}$` (e.g., `DEF-01`, `STATE`).
3. Links binder to an existing legal case.
4. Initializes binder status as `BinderStatus.DRAFT`.

### B. Adding Exhibits & Calculating Bates Ranges (`POST /api/binders/{binderId}/exhibits`)
1. **Immutability Check:**
    - If `binder.getStatus()` is `FINALIZED` or `SERVED`, throws `BadRequestException`. No additions or modifications are permitted on locked binders.
2. **Case Boundary Validation:**
    - Verifies that the evidence item belongs to the same case as the binder. Mixing case files throws `BadRequestException`.
3. **Duplicate Prevention:**
    - Checks `binderExhibitRepository.existsByBinderIdAndEvidenceItemId()`. Adding the same item twice throws `BadRequestException`.
4. **Consecutive Bates Number Calculation:**
    - Queries existing exhibits ordered by `batesStartNum` ascending.
    - If empty: `nextBatesStart = 1`.
    - If exhibits already exist: reads `batesEndNum` of the latest exhibit:
      $$\text{nextBatesStart} = \text{lastExhibit.batesEndNum} + 1$$
      $$\text{batesEnd} = \text{nextBatesStart} + \text{request.pageCount} - 1$$
5. Persists `BinderExhibit` association.
6. Returns formatted Bates labels in the response (e.g., `DEF-0001` through `DEF-0024`).

### C. Locking & Immutability (`PATCH /api/binders/{binderId}/status`)
- State transitions: `DRAFT` -> `UNDER_REVIEW` -> `FINALIZED` -> `SERVED`.
- Once marked `SERVED`, any attempt to revert or modify status throws `BadRequestException`.

---

## 4. API Endpoints

| HTTP Method | Endpoint | Required Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/binders` | `ROLE_ATTORNEY`, `ROLE_ADMIN` | Creates a new trial binder with a Bates prefix. |
| `POST` | `/api/binders/{binderId}/exhibits` | `ROLE_ATTORNEY`, `ROLE_ADMIN` | Attaches evidence and calculates Bates numbering range. |
| `PATCH` | `/api/binders/{binderId}/status` | `ROLE_ATTORNEY`, `ROLE_ADMIN` | Updates status (e.g., locks binder as `FINALIZED`). |
| `GET` | `/api/binders/{id}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Retrieves binder with exhibits and Bates ranges. |
| `GET` | `/api/binders/case/{caseId}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Lists all binders tied to a specific case. |

---

## 5. Business Rules & Edge Cases
- **Zero Page Gap Guarantee:** Because offsets are derived from the prior exhibit's `batesEndNum + 1`, Bates numbering sequences are contiguous without numbering gaps.
- **Prefix Consistency:** Every item in the binder shares the parent binder's Bates prefix (e.g., prefix `STATE` formats all items as `STATE-0001`, `STATE-0002`).