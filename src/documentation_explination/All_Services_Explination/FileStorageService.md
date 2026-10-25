# FileStorageService Explanation

## What is FileStorageService?

When handling digital evidence, such as high-resolution crime scene photos, body camera videos, PDF contracts, or mobile phone forensic dumps, storing gigabytes of binary data directly inside relational database tables (like PostgreSQL BLOBs) degrades database performance, bloats backups, and causes severe memory overhead.

`FileStorageService` is the dedicated filesystem storage manager for VeritasVault. It decouples heavy binary files from the database:
* **The Disk:** Stores raw physical bytes safely on the server's local file system in designated subfolders (e.g. `uploads/evidence`, `uploads/avatars`).
* **The Database:** Stores only a lightweight, standardized relative path string (e.g., `evidence/b1a4-49c7.pdf`).

In addition to storage, this service implements critical defense-in-depth security measures to protect the server from file injection and path traversal attacks, and includes a live forensic tampering simulation mechanism.

---

## Variables Being Used and Their Reasons:

* **`rootStorageLocation`**: A Java `java.nio.file.Path` pointing to the root storage directory on disk (configured via `${app.storage.upload-dir:uploads}`). 
  * In the constructor, this path is converted to an absolute path and normalized (`.toAbsolutePath().normalize()`).
  * If the directory does not exist on server startup, `Files.createDirectories` automatically provisions it.

---

## Methods and Their Implementation Explained:

### 1. `storeFile(MultipartFile file, String subfolder)`: [Secure File Ingestion]
* **Empty File Guard:** Rejects 0-byte uploads (`file.isEmpty()`) with `BadRequestException`.
* **Path Traversal Defense (Step 1):** 
  * Cleans the incoming filename via `StringUtils.cleanPath(...)`.
  * If the filename contains `".."`, it rejects the upload immediately to stop malicious attempts to climb up into system directories (like `/etc/passwd` or `C:\Windows`).
* **UUID Anonymization & Collision Prevention:**
  * Extracts the original file extension (e.g., `.png`, `.pdf`).
  * Replaces the user's filename with a universally unique identifier: `UUID.randomUUID() + fileExtension`.
  * **Why?** If two different officers upload a file named `report.pdf`, the UUID prevents them from overwriting each other or executing scripts with dangerous characters in their names.
* **Path Traversal Defense (Step 2 - Target Boundary Check):**
  * Resolves the target folder and target file path.
  * Checks `if (!targetPath.startsWith(this.rootStorageLocation))`: if the resolved path escapes outside the designated storage root, throws an unauthorized storage error.
* **Atomic Stream Copy:**
  * Uses `Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING)` to stream bytes directly from network input to disk without loading the whole file into server RAM.
* **Return Value:** Returns a normalized relative path with forward slashes (e.g., `evidence/3fa85f64-5717-4562-b3fc-2c963f66afa6.pdf`) for storage in the database.

### 2. `loadAsResource(String relativePath)`: [Safe File Streaming]
* Resolves the relative path against `this.rootStorageLocation`.
* Enforces the boundary security check (`!filePath.startsWith(...)`) so attackers cannot pass arbitrary paths like `../../Windows/System32`.
* Wraps the path in a Spring `UrlResource(filePath.toUri())`.
* Checks `resource.exists() && resource.isReadable()`. If the file is missing or unreadable, throws `ResourceNotFoundException`.
* Returns the `Resource` for streaming in REST controllers.

### 3. `deleteFile(String relativePath)`: [Safe File Cleanup]
* Validates that the path string is not blank.
* Confirms the path is within `rootStorageLocation`.
* Calls `Files.deleteIfExists(filePath)` inside a safe `try-catch` block so cleanup failures never crash an ongoing database transaction.

### 4. `appendTamperByte(String relativePath)`: [Forensic Tamper Simulation]
* Developed specifically for **Unit 3 Concurrency & Lab Verification testing**.
* Finds the target file on disk and appends raw byte content (`\n[TAMPERED_BYTE_IN_LAB]`) using `StandardOpenOption.APPEND`.
* When the examiner subsequently checks the evidence back into the vault, the background `labVerificationExecutor` thread recalculates the SHA-256 hash, discovers that the hash no longer matches the baseline, and triggers an automated `COMPROMISED` and `SEQUESTERED` alert.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why save UUID filenames instead of keeping original file names?** | Preserving original names exposes servers to duplicate collisions, special character exploits, and remote code execution vulnerabilities. UUIDs ensure every file on disk is unique and sanitized. |
| **Why check `!targetPath.startsWith(rootStorageLocation)`?** | This is the standard defence against Path Traversal (Directory Traversal) attacks, ensuring an attacker cannot break out of the `uploads/` sandbox. |
| **Why use input streams instead of byte arrays?** | Reading entire 2GB forensic disk images into byte arrays in Java memory (`byte[]`) would immediately trigger `OutOfMemoryError`. Input streams stream data chunk by chunk with minimal RAM footprint. |
