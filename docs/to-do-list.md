# VeritasVault - Engineering Roadmap & Sprint Execution

<div align="center">

| Duration | Feature Modules | Database Architecture | Security & Concurrency | Test Coverage |
| :---: | :---: | :---: | :---: | :---: |
| **Complete** | **11 Spring Services** | **10 PostgreSQL Tables** | **Stateless JWT + Pessimistic Locking + 4-Thread Pool** | ![Passing](https://img.shields.io/badge/JUnit5-34%2F34%20Passing-brightgreen?style=flat-square) |

</div>

---

### Project Execution Pipeline

```mermaid
flowchart TD
    M1["<b>Module 01: Auth & Identity</b><br/>JWT, BCrypt, 24-hr SMTP Token"] --> M2["<b>Module 02: Case Management</b><br/>Dockets, Lead Counsel & Client Scoping"]
    M2 --> M3["<b>Module 03: Evidence Ingestion</b><br/>Stream SHA-256 Hashing & Barcode Generator"]
    M3 --> M4["<b>Module 04: Chain of Custody</b><br/>Append-Only Immutable Ledger (Rule 901)"]
    M4 --> M5["<b>Module 05: Lab Reservations</b><br/>Pessimistic Locks & Real-Time SSE Streams"]
    M5 --> M6["<b>Module 06: Exhibit Binders</b><br/>Bates Numbering & Continuous Re-indexing"]
    M6 --> M7["<b>Module 07: Discovery Production</b><br/>Expiring Tokens & IP/User-Agent Delivery Receipts"]
    M7 --> M8["<b>Module 08: Unit 3 Concurrency & Verification</b><br/>ExecutorService 4-Thread Pool & 10th Table (Lab Reports)"]

    style M1 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M2 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M3 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M4 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M5 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M6 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M7 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style M8 fill:#064e3b,stroke:#10b981,stroke-width:3px,color:#fff
```

---

### Sprint Milestones Completed

- [x] **Milestone 1:** Stateless Authentication, Role-Based Access Control (`ROLE_ADMIN`, `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_CLIENT`), and Email Verification.
- [x] **Milestone 2:** Legal Case Docketing with Client Scoping to prevent unauthorized cross-client case visibility.
- [x] **Milestone 3:** Cryptographic Evidence Ingestion with real-time SHA-256 checksums and automated tracking barcodes (`EV-YYYY-XXXX`).
- [x] **Milestone 4:** Append-Only Chain of Custody Ledger under Federal Rule of Evidence 901.
- [x] **Milestone 5:** Trial Exhibit Binders with automated Bates Numbering (`STATE-V-DOE-0001`) and gapless re-indexing cascades.
- [x] **Milestone 6:** Outbound Discovery Serving with SHA-256 hashed download tokens and certified delivery receipts (IP + browser User-Agent logs).
- [x] **Milestone 7 (Unit 3 Concurrency):** Database-level pessimistic locking (`SELECT ... FOR UPDATE`) to prevent simultaneous booking collisions.
- [x] **Milestone 8 (Unit 3 Multithreading):** 4-worker `ExecutorService` thread pool (`lab-verifier-pool-1` to `4`), 10th table (`forensic_lab_reports`), and automated `SEQUESTERED` quarantine for tampered evidence.
- [x] **Milestone 9:** Real-time Server-Sent Events (SSE) telemetry feed using `CopyOnWriteArrayList<SseEmitter>`.
- [x] **Milestone 10:** 34/34 automated JUnit 5 & Concurrency tests passing cleanly.