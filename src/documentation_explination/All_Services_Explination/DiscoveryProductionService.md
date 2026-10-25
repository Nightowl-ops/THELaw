# DiscoveryProductionService Explanation

## What is DiscoveryProductionService?

In civil and criminal litigation, the **Discovery Phase** is the formal process where opposing parties must exchange evidence, documents, and witness statements before trial. 

Under legal rules (such as Rule 26 and Rule 34 of the Federal Rules of Civil Procedure), attorneys cannot secretly hide evidence, nor can opposing counsel claim they "never received the file."

`DiscoveryProductionService` handles the electronic serving and distribution of finalized trial binders to external parties (opposing attorneys, insurance adjusters, or the court). 

It solves three critical legal challenges:
1. **Cryptographic Token Sharing:** Generates secure, unguessable, ephemeral download links without requiring external opposing attorneys to create permanent internal accounts.
2. **Defensive Token Hashing:** Stores SHA-256 hashes of the access tokens in the database to prevent token leakage from raw database compromises.
3. **Certified Legal Delivery Receipts:** Records an immutable audit log every time a file is downloaded, capturing the exact timestamp, IP address, and browser User-Agent, giving attorneys undeniable courtroom proof of service.

---

## Variables Being Used and Their Reasons:

* **`discoveryProductionRepository`**: Talks to the `discovery_productions` table in PostgreSQL. Saves discovery production packages, recipient party names, expiration dates, and token hashes.
* **`discoveryAccessLogRepository`**: Talks to the `discovery_access_logs` table. Stores the courtroom access receipts (IP address, client User-Agent, download timestamp).
* **`legalCaseRepository`**: Validates the case ID associated with the discovery production.
* **`exhibitBinderRepository`**: Verifies the trial binder being served and transitions its status to `SERVED`.
* **`binderExhibitRepository`**: Retrieves the list of exhibits and page ranges linked to the binder being distributed.
* **`userRepository`**: Confirms the identity of the producing attorney or administrator.
* **`fileStorageService`**: Loads the actual evidence binary file from disk (`uploads/` folder) so it can be streamed to the downloader.
* **`evidenceService`**: Maps internal evidence items into formatted DTO responses for external bundle previews.
* **`baseUrl`**: Injected via Spring's `@Value("${app.server.base-url:http://localhost:8085}")`. Constructs fully-qualified download URLs (e.g. `http://localhost:8085/api/discovery/download/{token}`).

---

## Methods and Their Implementation Explained:

### 1. `createProduction(request, binderId, currentUserId)`: [Serving Discovery]
* **Authorization & Validation:**
  * Ensures the legal case and binder exist, and that the binder belongs to the specified case.
  * Ensures the user holds `ROLE_ATTORNEY` or `ROLE_ADMIN`.
  * Verifies `request.getExpiresAt().isBefore(LocalDateTime.now())`: Expiration must be set in the future.
* **Cryptographic Token Generation:**
  * Generates an unguessable raw token: `UUID.randomUUID().toString()`.
  * Computes the SHA-256 cryptographic digest of the token via `hashToken(rawAccessToken)`.
* **Persistence & Binder Lock:**
  * Builds and saves `DiscoveryProduction` with `isWatermarked = true`, the recipient party name, and the expiration date.
  * Transitions the `ExhibitBinder` status to **`SERVED`** and saves it. Once served, the binder is permanently locked from further editing.
* **Return:** Generates the secure download link and returns `DiscoveryResponse`.

### 2. `accessProductionByToken(rawAccessToken, ipAddress, userAgent)`: [Portal Preview]
* Validates the token against the database using `findByRawToken`.
* **Expiration Verification:** If `LocalDateTime.now().isAfter(production.getExpiresAt())`, immediately rejects access with `BadRequestException` ("This discovery package link has expired").
* **Access Logging:** Automatically creates a `DiscoveryAccessLog` recording who viewed the portal, their IP address, and browser header.
* Returns metadata for the recipient's web landing page.

### 3. `loadProductionBundle(rawAccessToken, ipAddress, userAgent)`: [Evidence Download & Receipt Generation]
* Re-verifies token validity and expiration.
* Fetches the exhibits attached to the binder in Bates number order.
* Retrieves the primary evidence item and calls `fileStorageService.loadAsResource(...)` to obtain a Spring `Resource` stream.
* **Court Receipt Generation:** Inserts an immutable row into `discovery_access_logs`:
  * `ipAddress`: IP of the downloading computer (e.g., `192.168.1.50` or external ISP IP).
  * `userAgent`: Exact browser/client used (e.g., Chrome, Edge, Safari).
  * `downloadedFileName`: Standardized filename combining the binder name and exhibit extension.
* Returns a `DownloadBundle` record containing the Spring `Resource` and filename for HTTP streaming.

### 4. `getReceipts(productionId)`: [Proof of Service Audit Trail]
* Queries `discoveryAccessLogRepository.findByProductionIdOrderByAccessedAtDesc(productionId)`.
* Produces a chronological list of `DiscoveryReceiptResponse` objects.
* If opposing counsel claims "We never got the file!", the producing attorney can present these receipts to the judge showing exact download times, IP addresses, and client fingerprints.

### 5. `hashToken(rawToken)` & `findByRawToken(rawToken)`
* Uses Java's `MessageDigest.getInstance("SHA-256")` and `HexFormat` to turn the raw UUID into a 64-character hexadecimal digest.
* Looks up the package by hash first, with fallback to the raw token for backward compatibility.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why use raw token vs. SHA-256 token hash?** | Same principle as password hashing. If an attacker reads the database dump, they only see token hashes, preventing them from accessing unexpired discovery files without the original link. |
| **Why capture IP Address and User-Agent?** | In legal disputes, proof of service is paramount. Under court evidentiary standards, timestamped server logs recording IP and browser headers provide legally admissible proof that opposing counsel received the files. |
| **Why does creating a production set the binder to `SERVED`?** | Legal ethics and court rules prohibit modifying documents after they have been officially served to opposing parties. Freezing the binder guarantees that the court record remains uncorrupted. |
