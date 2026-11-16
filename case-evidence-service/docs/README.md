# Veritas Case & Evidence Intake Microservice (`case-evidence-service`)

> **Port:** `8082`  
> **Database:** `veritas_db`  
> **Package:** `com.veritasvault.caseevidence`  

This microservice manages all official legal cases, digital evidence files, Bates-numbered exhibit binders, and discovery production packages. It runs independently on port 8082 and shares the single database (`veritas_db`).

---

## 🏛️ Domain Architecture & Entities

| Entity / Table | Responsibilities | Key Fields & Decoupled IDs |
| :--- | :--- | :--- |
| **`LegalCase`** (`legal_cases`) | Court dockets, case numbers, statuses, assignments | `leadAttorneyId`, `clientId`, `caseNumber`, `caseTitle`, `caseType`, `status` |
| **`EvidenceItem`** (`evidence_items`) | Digital evidence files, streaming SHA-256 fingerprints | `case_id`, `itemTrackingCode`, `uploadedByUserId`, `sha256Hash`, `storageUrl`, `status` |
| **`ExhibitBinder`** (`exhibit_binders`) | Trial binders with customizable Bates prefixes | `case_id`, `binderName`, `batesPrefix`, `status`, `createdByUserId` |
| **`BinderExhibit`** (`binder_exhibits`) | Bates ranges attached to evidence items | `binder_id`, `evidence_id`, `batesStartNum`, `batesEndNum` |
| **`DiscoveryProduction`** (`discovery_productions`) | Opposing counsel discovery packages | `case_id`, `binder_id`, `accessTokenHash`, `expiresAt`, `createdByUserId` |
| **`DiscoveryAccessLog`** (`discovery_access_logs`) | Proof of Service audit logs | `production_id`, `ipAddress`, `userAgent`, `accessedAt` |

---

## 🔗 Inter-Service Communication

### 1. Inbound Authentication (JWT - Zero Network Overhead)
* Validates Bearer JWT tokens issued by `auth-service` (port 8081).
* Extracts `userId`, `role`, and `fullName` directly from token claims.
* Enforces role security via `@PreAuthorize`.

### 2. Outbound Synchronous HTTP Validation (Service 2 ➔ Service 1)
* **Endpoint called:** `GET http://localhost:8081/api/users/{userId}`
* **Purpose:** Validates that `leadAttorneyId` has `ROLE_ATTORNEY` and `clientId` has `ROLE_CLIENT` before creating a case.
* **Fail-Safe:** Returns `400 Bad Request` if the user does not exist in `auth-service`.

### 3. Asynchronous Kafka Plumbing (Ready for Service 3)
* **Publisher:** Emits `evidence-ingested-topic` when evidence is uploaded.
* **Subscriber:** Listens to `evidence-tampered-topic` from Service 3 (`forensic-vault-service`) and automatically quarantines tampered evidence (`SEQUESTERED`).

---

## 🔑 Pre-Seeded Benchmark Data

On startup, `DatabaseSeeder.java` automatically seeds:
1. **Benchmark Case:**
   * **Case Number:** `CR-2026-0041`
   * **Title:** `State v. Sterling`
   * **Type:** `DEFENSE`
   * **Lead Attorney:** ID `2` (Sarah Jenkins, Esq.)
   * **Client:** ID `4` (David Sterling)
2. **Benchmark Digital Evidence:**
   * **Tracking Code:** `EV-2026-0001`
   * **Name:** `Forensic Phone Extraction - iPhone 15 Pro`
   * **SHA-256 Hash:** `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
   * **Status:** `AVAILABLE`

---

## 📡 Complete API Reference

All requests require header: `Authorization: Bearer <TOKEN>` (except public discovery links).

### 1. Legal Cases (`/api/cases`)
* `POST /api/cases` - Create legal case (Attorneys, Admins).
* `GET /api/cases` - List cases with pagination, search, and client isolation.
* `GET /api/cases/{id}` - Get case dossier.
* `PATCH /api/cases/{id}/status?status=PENDING_TRIAL` - Update case lifecycle.
* `GET /api/cases/attorney/{attorneyId}` - List cases for specific attorney.

### 2. Evidence Vault (`/api/evidence`)
* `POST /api/evidence/upload` - Upload file (multipart), compute streaming SHA-256, and ingest.
* `GET /api/evidence/{id}` - Get evidence metadata.
* `GET /api/evidence/tracking/{trackingCode}` - Lookup by court tracking code (`EV-YYYY-XXXX`).
* `GET /api/evidence/case/{caseId}` - List evidence for case with status/type filters.
* `GET /api/evidence/{id}/download` - Stream binary file from storage.
* `PATCH /api/evidence/{id}/status?status=AVAILABLE` - Update operational status.
* `POST /api/evidence/{id}/simulate-tamper` - Test endpoint: Appends corrupt byte to disk.

### 3. Exhibit Binders & Bates Numbering (`/api/binders`)
* `POST /api/binders` - Create draft exhibit binder with custom Bates prefix (e.g. `DEF-01`).
* `POST /api/binders/{id}/exhibits` - Append evidence item and calculate consecutive Bates ranges.
* `DELETE /api/binders/{id}/exhibits/{exhibitId}` - Remove exhibit and automatically re-sequence remaining Bates numbers.
* `PATCH /api/binders/{id}/status?status=FINALIZED` - Lock binder (once FINALIZED, immutable).
* `GET /api/binders/{id}` - Get binder with all exhibits and Bates index.
* `GET /api/binders/case/{caseId}` - List binders for case.

### 4. Discovery Production (`/api/discovery`)
* `POST /api/discovery/produce/{binderId}` - Lock binder as `SERVED` and generate secure external access token.
* `GET /api/discovery/access/{accessToken}` - **Public:** Opposing counsel portal; logs Proof of Service IP.
* `GET /api/discovery/download/{accessToken}` - **Public:** Stream complete evidence bundle.
* `GET /api/discovery/{id}/receipts` - View Proof of Service download audit logs.
