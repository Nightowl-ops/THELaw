# LegalCaseService Explanation

## What is LegalCaseService?

In legal jurisprudence, evidence items, trial exhibits, custody logs, and forensic reports do not float independently; they belong to a formal legal matter, such as a criminal indictment, civil breach-of-contract lawsuit, or personal injury claim.

`LegalCaseService` is the foundational organizational engine of VeritasVault. It establishes the legal docket under which all evidence and exhibits are anchored. 

It manages:
1. **Case Docket Management:** Enforces court case numbering rules, case titles, case categories (Criminal, Civil, Corporate, Family, Probate), and lifecycle statuses (`ACTIVE`, `PENDING_TRIAL`, `CLOSED`, `ARCHIVED`).
2. **Legal Representation Linking:** Explicitly pairs the lead attorney with their client, while tracking opposing counsel and opposing parties for formal discovery.
3. **Role-Based Access Partitioning:** Enforces strict client data isolation. While attorneys and administrators can view all cases across the firm, client users (`ROLE_CLIENT`) are strictly compartmentalized and can only access the specific case in which they are named as the client.

---

## Variables Being Used and Their Reasons:

* **`legalCaseRepository`**: Interfaces directly with the `legal_cases` table in PostgreSQL. Executes case lookups, unique case number checks, and filtered search queries (by case type, status, client ID, or search keyword).
* **`userRepository`**: Validates user IDs provided during case creation. Confirms that the lead attorney holds attorney credentials (`ROLE_ATTORNEY` or `ROLE_ADMIN`) and that the optional assigned client holds client credentials (`ROLE_CLIENT`).

---

## Methods and Their Implementation Explained:

### 1. `createCase(CreateCaseRequest request)`: [Opening a Legal Matter]
* **Unique Case Number Enforcement:**
  * Checks `legalCaseRepository.existsByCaseNumber(request.getCaseNumber())`.
  * If a case with that docket number already exists, rejects the creation with `ConflictException` (**`409 Conflict`**).
* **Lead Counsel Validation:**
  * Finds the attorney by `request.getLeadAttorneyId()`.
  * Checks `if (attorney.getRole() != Role.ROLE_ATTORNEY && attorney.getRole() != Role.ROLE_ADMIN)`.
  * Rejects with `BadRequestException` if a non-attorney (like a client or examiner) is assigned as the lead legal counsel.
* **Client Verification (Optional):**
  * If `clientId` is provided, fetches the user and verifies that their role is strictly `ROLE_CLIENT`.
* **Docket Construction & Status:**
  * Builds the `LegalCase` entity with default status `CaseStatus.ACTIVE`.
  * Attaches opposing party metadata: `opposingPartyName`, `opposingCounselName`, `opposingCounselEmail` (used later by `DiscoveryProductionService` for serving discovery bundles).
* **Return:** Saves the case and returns a `CaseResponse` DTO.

### 2. `getCaseById(Long id, User currentUser)`: [Client Privacy & Access Control]
* Looks up the legal case by numeric database ID.
* **Client Isolation Check:**
  * `if (currentUser.getRole() == Role.ROLE_CLIENT)`:
  * Verifies whether `legalCase.getClient() != null && legalCase.getClient().getId().equals(currentUser.getId())`.
  * If the client attempts to view a case that belongs to someone else, the service immediately throws `AccessDeniedException` (**`403 Forbidden`**).
* Attorneys and Administrators bypass this restriction to oversee the entire docket.

### 3. `getCaseById(Long id)`: [Administrative / Internal Lookup]
* Direct overload that retrieves the case without client-scoping checks. Used by other internal services (e.g. `EvidenceService` or `ExhibitBinderService`) when verifying foreign key relationships.

### 4. `getCases(CaseStatus status, CaseType type, String search, User currentUser, Pageable pageable)`: [Scoped Docket Search]
* Annotated with `@Transactional(readOnly = true)`.
* Trims and sanitizes the search keyword query.
* **Client-Aware Branching:**
  * **For Clients:** Automatically invokes `legalCaseRepository.searchCasesByClient(...)`, ensuring search results only contain cases belonging to that specific client ID.
  * **For Attorneys / Admins:** Invokes `legalCaseRepository.searchCases(...)`, allowing cross-firm searching across all active and archived cases with pagination and multi-criteria filters.

### 5. `getAllCases()` & `getCasesForAttorney(Long attorneyId)`
* `getAllCases()`: Returns an unpaged list of every case in the system, utilized by frontend dropdown menus when filing evidence or selecting binders.
* `getCasesForAttorney(...)`: Returns all active and historical cases assigned to a specific lead attorney, powering attorney-specific dashboard views.

### 6. `updateCaseStatus(Long id, CaseStatus newStatus)`: [Case Lifecycle Transition]
* Loads the case by ID.
* Updates the case status across standard litigation stages:
  * `ACTIVE`: Ongoing investigation and trial preparation.
  * `PENDING_TRIAL`: Evidence is locked and court dates are set.
  * `CLOSED`: Verdict rendered or settlement reached.
  * `ARCHIVED`: Retained for statutory recordkeeping.
* Saves and returns the updated case response.

### 7. `mapToResponse(LegalCase legalCase)`: [DTO Transformer]
* Transforms the nested `LegalCase` relational entity into a flat, consumer-friendly `CaseResponse` DTO.
* Safely extracts and flattens lead attorney details (ID, full name, email) and client details (ID, full name, email) while preventing circular reference serialization bugs.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why enforce unique case numbers in the service layer?** | Court docket numbers (e.g., `CR-2026-0042`) are universal identifiers used in legal filings. Enforcing uniqueness prevents confusing cross-case evidence contamination. |
| **How does client isolation prevent legal data leakage?** | Clients often share the same portal, but legal confidentiality (attorney-client privilege) mandates strict data isolation. Client-scoped queries guarantee a client can never see or search another client's legal files. |
| **Why capture opposing counsel email during case creation?** | In the discovery phase, Rule 26 requires serving evidence packages to opposing counsel. Having verified opposing party contacts upfront allows automated delivery via `DiscoveryProductionService`. |