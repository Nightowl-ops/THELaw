# 07 - Discovery Production, Secure Sharing & Proof of Service

## 1. Functional Overview
The Discovery Production module handles formal discovery disclosures to opposing counsel. It produces secure, time-expiring access tokens and tracks download attempts to establish legal Proof of Service.

Rather than sending sensitive forensic assets over unsecured email attachments, VeritasVault generates a unique, opaque token URL. Every external retrieval records client IP addresses, timestamps, and browser user-agents in an audit table.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.DiscoveryProduction`
    - `com.veritasvault.model.DiscoveryAccessLog`
    - `com.veritasvault.model.ExhibitBinder`
    - `com.veritasvault.model.LegalCase`
    - `com.veritasvault.model.enums.BinderStatus`
- **Repositories:**
    - `com.veritasvault.repository.DiscoveryProductionRepository`
    - `com.veritasvault.repository.DiscoveryAccessLogRepository`
    - `com.veritasvault.repository.ExhibitBinderRepository`
    - `com.veritasvault.repository.LegalCaseRepository`
- **Services:**
    - `com.veritasvault.service.DiscoveryProductionService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.DiscoveryController`
    - `com.veritasvault.dto.request.CreateDiscoveryRequest`
    - `com.veritasvault.dto.response.DiscoveryResponse`

---

## 3. Workflow & Logic Sequence

### A. Creating a Production Package (`POST /api/discovery/produce/{binderId}`)
1. Guarded by `@PreAuthorize("hasAnyRole('ROLE_ATTORNEY', 'ROLE_ADMIN')")`.
2. Validates that the case and exhibit binder match.
3. Asserts `expiresAt` is strictly in the future.
4. Generates an opaque, unguessable cryptographic token:
   ```java
   String accessToken = UUID.randomUUID().toString();