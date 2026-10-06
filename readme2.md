# VeritasVault: Digital Evidence Vault & Chain-of-Custody Management System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue.svg)](https://www.postgresql.org/)
[![Security](https://img.shields.io/badge/Spring%20Security-6%20Stateless%20JWT-red.svg)](https://spring.io/projects/spring-security)
[![Swagger](https://img.shields.io/badge/OpenAPI%203-Swagger%20UI-yellow.svg)](http://localhost:8085/swagger-ui/index.html)

**VeritasVault** is an enterprise-grade digital forensics vault and legal custody lifecycle platform built for law firms, law enforcement forensics labs, and judicial counsel. The platform guarantees strict forensic integrity for digital assets (device extractions, video/audio dumps, network logs) through streaming cryptographic verification, immutable chain-of-custody ledgers, laboratory booking conflict detection, trial exhibit Bates stamping, and secure time-limited discovery productions.

---

## Technical Stack & Architecture

| Layer | Technologies |
| :--- | :--- |
| **Core Framework** | Spring Boot 3.3.4, Java 17 |
| **Persistence & Database** | Spring Data JPA, Hibernate 6.5, PostgreSQL |
| **Security & Auth** | Spring Security 6 (Stateless JWT / HMAC-SHA256), Jakarta Mail |
| **Real-Time Telemetry** | Server-Sent Events (SSE via `SseEmitter`) |
| **API Documentation** | SpringDoc OpenAPI 3.0 / Swagger UI (Port 8085) |
| **Cryptographic Utilities** | Streaming MessageDigest SHA-256 (`ChecksumUtils`) |
| **Build & Tooling** | Apache Maven 3.9+, Lombok |

---

## System Documentation Index

The technical documentation is organized modularly inside the [`docs/`](./docs) directory. Each document covers the technical architecture, class dependencies, workflow steps, endpoint definitions, and edge cases for that functional domain:

| Module | Documentation File | Core Functionality |
| :---: | :--- | :--- |
| **01** | [**Auth & Identity Lifecycle**](./docs/01-auth-and-security.md) | User onboarding, 24h UUID activation token dispatch, BCrypt hashing, and stateless JWT issuance. |
| **02** | [**Legal Case Management**](./docs/02-case-management.md) | Relational root for legal proceedings, lead attorney assignment, and client dossier linking. |
| **03** | [**Evidence Intake & Hashing**](./docs/03-evidence-and-hashing.md) | Streaming SHA-256 computation, path traversal guards, storage allocation, and intake logging. |
| **04** | [**Chain of Custody Ledger**](./docs/04-chain-of-custody.md) | Append-only, tamper-evident chronological ledger tracking all custody transitions and hashes. |
| **05** | [**Lab Scheduling & Telemetry**](./docs/05-lab-reservations-and-sse.md) | Workstation double-booking collision engine and real-time SSE event broadcasts. |
| **06** | [**Exhibit Binders & Bates**](./docs/06-binders-and-bates.md) | Trial binder bundling, consecutive page Bates offset calculations, and immutability locks. |
| **07** | [**Discovery Production**](./docs/07-discovery-production.md) | Token-gated opposing counsel disclosures, link expiration, and Proof of Service access logging. |

---

## Quickstart & Local Setup

### 1. Prerequisites
- **JDK 17** or higher
- **PostgreSQL 14+** running locally on port `5432`
- **Apache Maven 3.8+** (or use `./mvnw`)

### 2. Database Initialization
Create the development and test databases in PostgreSQL:
```sql
CREATE DATABASE veritasvault;
CREATE DATABASE veritasvault_test;