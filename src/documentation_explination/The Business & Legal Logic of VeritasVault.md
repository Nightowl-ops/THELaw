# VeritasVault

> **Enterprise-grade, cryptographically verifiable digital evidence and chain-of-custody management.**

---

## 1. The Legal Problem: Why Evidence Gets Thrown Out

In both criminal and civil law, having evidence is not enough. You must prove to a judge that the evidence is **authentic, pristine, and untampered**.

> **Federal Rules of Evidence (FRE Rule 901  Authenticating or Identifying Evidence):**  
> *"The proponent must produce evidence sufficient to support a finding that the item is what the proponent claims it is."*

If opposing counsel can plant reasonable doubt that a file was modified, cropped, accidentally overwritten, or handled by an unauthorized party while in your custody:
* The judge will rule that the evidence has suffered **spoliation**.
* The court orders the evidence **suppressed** (inadmissible at trial).
* **The impact:** Murder charges get dismissed, and multi-million-dollar lawsuits are lost before reaching the jury.

---

## 2. Real-World Case Studies That Motivated VeritasVault

Every core feature in VeritasVault was engineered to prevent catastrophic failures from actual legal history.

### Case 1: The O.J. Simpson Murder Trial (1995)
**The Vulnerability:** Broken Chain of Custody

* **What Happened:** A vial containing O.J. Simpson’s reference blood was collected by a nurse. Instead of being immediately logged into a secure vault, a detective carried the vial in his coat pocket for several hours before booking it into evidence.
* **The Result:** The defense argued the sample could have been planted or contaminated during those unlogged hours. That gap in the custody timeline created fatal reasonable doubt, securing an acquittal.
* **How VeritasVault Solves This:** **Append-Only Chain-of-Custody Ledger.** The moment evidence enters the system, an `INGEST` record is permanently stamped. Every checkout, transfer, and check-in records:
  * Who accessed the asset
  * Exact timestamp and rationale
  * SHA-256 cryptographic hash at that exact second

---

### Case 2: Qualcomm Inc. v. Broadcom Corp. (2008)
**The Vulnerability:** Discovery Concealment and Proof of Service

* **What Happened:** In a high-stakes patent dispute, Qualcomm was legally obligated to produce all relevant emails during discovery. Qualcomm withheld over 46,000 critical emails, claiming they "didn't exist." Broadcom uncovered the withheld records during trial.
* **The Result:** The court levied **$8.5 million in sanctions** against Qualcomm, referred defense counsel to the State Bar for ethics investigations, and barred Qualcomm from enforcing key patents.
* **How VeritasVault Solves This:** **Token-Hashed Discovery Productions & Proof of Service.** When discovery is served:
  * Generates an itemized, manifest-locked evidence bundle.
  * Creates an ephemeral, cryptographically hashed download link.
  * Logs the recipient's exact download timestamp, user agent, and IP address.
  * Eliminates false claims of non-receipt.

---

### Case 3: The Annie Dookhan / Hinton State Lab Scandal (2012–2017)
**The Vulnerability:** Forensic Lab Tampering

* **What Happened:** Forensic chemist Annie Dookhan routinely "dry-labbed" samples (identifying substances by sight rather than running chemical analyses), forged signatures on custody transfer logs, and co-mingled test samples.
* **The Result:** The Massachusetts Supreme Court vacated and dismissed over **24,000 criminal convictions** due to systemic compromise of forensic integrity.
* **How VeritasVault Solves This:** **Cryptographic Re-Hashing on Check-In.** When an examiner returns evidence from testing, VeritasVault recalculates its SHA-256 hash. If even a single byte differs from the ingestion state, the check-in is blocked and the asset is instantly locked under `SEQUESTERED` quarantine status.

---

### Case 4: United States v. Ross Ulbricht (Silk Road, 2015)
**The Vulnerability:** Corrupt Internal Actors and Database Tampering

* **What Happened:** Two federal agents working on the Silk Road investigation leveraged administrative database access to divert Bitcoin to private accounts and alter log entries on seized servers to cover their tracks.
* **How VeritasVault Solves This:** **Strict RBAC & Immutability Guarantees.**
  * Privilege escalation is impossible via non-admin endpoints.
  * Finalized courtroom exhibit binders cannot be reverted to drafts.
  * Records are never hard-deleted. Deprovisioned staff are marked `INACTIVE`, preserving their historic signatures in the chain-of-custody ledger forever.

---

## 3. The 5 Core Legal Workflows


### Workflow A: Forensic Ingestion & Cryptographic Fingerprinting
* **Legal Standard:** Best Evidence Rule (FRE 1002 / 1003) Digital duplicates are admissible as originals only if proven bit-for-bit identical.
* **Business Logic:** When a 500 MB phone extraction, disk image, or dashcam file is uploaded, the vault computes a SHA-256 digest immediately upon stream completion.
* **The Guarantee:** SHA-256 collision resistance mathematically certifies that zero bits have altered since intake.

### Workflow B: Lab Workstation Reservations & Concurrency
* **Legal Standard:** Physical Evidence Preservation  Prevents simultaneous, conflicting, or destructive testing by competing analysts.
* **Business Logic:** Before handling evidence, examiners must reserve a custody window for that specific item.
* **The Guarantee:** PostgreSQL pessimistic row locking (`SELECT FOR UPDATE`). If two examiners submit overlapping reservation requests at the same millisecond, the database serializes them, rejecting the collision with `409 Conflict`.

### Workflow C: Trial Exhibit Binders & Bates Numbering
* **Legal Standard:** Courtroom Exhibit Standards  Every page presented to a judge or jury must carry a sequential, unique alphanumeric identifier.
* **Business Logic:**
  1. Attorney creates a binder with a set prefix (e.g., `PLTF`).
  2. Exhibit 1 (5 pages) receives labels `PLTF-000001` through `PLTF-000005`.
  3. Exhibit 2 (10 pages) automatically resumes at `PLTF-000006` through `PLTF-000015`.
  4. If Exhibit 1 is removed while in draft status, Exhibit 2 automatically re-indexes starting at `PLTF-000001`, preventing numbering gaps.
* **The Guarantee:** Once shifted to `FINALIZED` or `SERVED`, an immutability lock seals the binder. Pages can never be inserted, rearranged, or purged.

### Workflow D: Opposing Counsel Discovery Disclosure
* **Legal Standard:** Fed. R. Civ. P. 26 (Mandatory Disclosure Requirements).
* **Business Logic:** Opposing counsel cannot have platform accounts. VeritasVault generates an outbound disclosure email containing an expirable link backed by a random cryptographic token (e.g., 7-day TTL).
* **The Guarantee:** The token is stored in the database exclusively as a SHA-256 digest. Even a full database compromise cannot expose valid download links, as the plaintext token exists solely in the delivered notice.

---

### Workflow E: Legal Role Separation

Access control strictly mirrors judicial ethics and chain-of-custody protocols:

| Role | Scope | Key Capabilities |
| :--- | :--- | :--- |
| `ROLE_ADMIN` | Infrastructure & Governance | Manages accounts, audits access, soft-deletes departed personnel. |
| `ROLE_ATTORNEY` | Strategy & Discovery | Creates cases, compiles Bates binders, serves discovery bundles. |
| `ROLE_FORENSIC_EXAMINER` | Evidence Analysis | Reserves lab slots, checks assets in/out, runs analysis. |
| `ROLE_CLIENT` | Advisory Read-Only | Views case status dashboard only; no access to raw files or logs. |

#### Architectural Exclusions by Design
* **Why no `Judge` role?** Judicial canons demand strict neutrality. Judges never log into party-controlled software. The court receives certified, sealed Bates binders and cryptographically stamped verification certificates.
* **Why no `Police Officer` role?** Law enforcement agencies run dedicated CAD/RMS systems. Officer badge credentials, agency identifiers, and incident numbers are captured directly as immutable metadata during item intake.