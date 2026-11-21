# VeritasVault: Digital Evidence Vault and Chain of Custody Management System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7.0%20(KRaft)-black.svg)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker%20Compose-Orchestrated-blue.svg)](https://www.docker.com/)
[![AWS](https://img.shields.io/badge/AWS-RDS%20%2B%20EC2%20%2B%20ECR-orange.svg)](https://aws.amazon.com/)
[![CI/CD](https://img.shields.io/badge/Jenkins-Automated%20Pipeline-red.svg)](https://www.jenkins.io/)
[![Swagger](https://img.shields.io/badge/OpenAPI%203-Swagger%20UI-yellow.svg)](http://98.91.232.127:8080)

VeritasVault is an enterprise digital forensics vault and legal custody lifecycle platform built for law firms, forensic labs, judicial counsel, and litigation teams. It provides courtroom grade digital custody tracking, automated evidence hashing, lab scheduling, trial exhibit preparation, and discovery exchange.

> **Live Production Deployment URL:** [http://98.91.232.127:5173](http://98.91.232.127:5173)
>
> **Default Test Credentials:**
> * System Administrator: `admin@veritasvault.com` / `Password123!`
> * Forensic Examiner: `examiner@veritasvault.com` / `Password123!`
> * Trial Attorney: `attorney@veritasvault.com` / `Password123!`

In this production release (Unit 4), the monolithic architecture has been refactored into an independently scalable microservices platform, containerized with Docker, decoupled using Apache Kafka event streaming, cached with Spring Cache, verified with Cucumber BDD test suites, and deployed to AWS (RDS PostgreSQL, EC2, and ECR) through an automated Jenkins CI/CD pipeline.

---

## 1. System Architecture

```mermaid
flowchart TD
    subgraph ClientLayer["Client Layer"]
        Browser["User Browser / Client Application"]
    end

    subgraph EdgeLayer["Edge / Ingress Layer"]
        Frontend["React SPA (Nginx) :5173"]
        Gateway["Spring Cloud API Gateway :8080"]
    end

    subgraph MicroservicesLayer["Core Microservices Layer"]
        AuthSvc["Auth and Identity Service :8081\nJWT Auth, RBAC, User Profiles"]
        CaseSvc["Case and Evidence Service :8082\nCases, Evidence Vault, Trial Binders"]
        ForensicSvc["Forensic Vault and Custody Service :8083\nLab Scheduling, Custody Logs, Verification"]
    end

    subgraph EventBus["Asynchronous Event Streaming"]
        Kafka["Apache Kafka 3.7.0 (KRaft Mode) :9092\nTopic: evidence-intake-events"]
    end

    subgraph Persistence["Cloud Persistence Layer"]
        RDS[("AWS RDS PostgreSQL 16\nDatabase: veritas_db :5432")]
    end

    Browser -->|HTTP 5173| Frontend
    Frontend -->|Reverse Proxy /api| Gateway
    Gateway -->|HTTP 8081| AuthSvc
    Gateway -->|HTTP 8082| CaseSvc
    Gateway -->|HTTP 8083| ForensicSvc

    CaseSvc -->|Publish Evidence Ingested| Kafka
    Kafka -->|Consume for Audit & Quarantine| ForensicSvc

    AuthSvc -->|JDBC| RDS
    CaseSvc -->|JDBC| RDS
    ForensicSvc -->|JDBC| RDS
```

---

## 2. Microservice Boundaries and Responsibilities

The system is decomposed into business capabilities where each service owns its functionality, configuration, dependencies, and APIs:

| Microservice | Port | Business Responsibility | Communication |
| :--- | :---: | :--- | :--- |
| **API Gateway** | `8080` | Reverse proxy, path routing, unified CORS handling, and edge request dispatching. | Synchronous HTTP / Spring Cloud Gateway |
| **Auth Service** | `8081` | User registration, authentication, JWT issuing and validation, profile storage, and role based access control (Admin, Attorney, Examiner, Client). | Synchronous REST |
| **Case and Evidence Service** | `8082` | Legal dockets, evidence file ingestion, SHA-256 integrity checksum calculation, Bates stamped exhibit binders, and discovery exchange. | Synchronous REST + Kafka Producer |
| **Forensic Vault Service** | `8083` | Chain of custody logging, physical evidence workstation reservations, tamper audits, and real time Server-Sent Events (SSE). | Synchronous REST + Kafka Consumer |
| **React Frontend** | `5173` | Production React SPA bundled via Vite and served with Nginx reverse proxy routing. | Browser UI to Gateway |

---

## 3. Asynchronous Messaging with Apache Kafka

To decouple evidence intake from downstream forensic auditing, Apache Kafka operates in modern KRaft mode (no Zookeeper required).

```mermaid
sequenceDiagram
    autonumber
    actor Investigator as Forensic Examiner
    participant Gateway as API Gateway (:8080)
    participant CaseSvc as Case & Evidence Service (:8082)
    participant Kafka as Apache Kafka Topic (:9092)
    participant ForensicSvc as Forensic Vault Service (:8083)
    participant Database as AWS RDS PostgreSQL

    Investigator->>Gateway: POST /api/evidence (Upload file & metadata)
    Gateway->>CaseSvc: Forward evidence payload
    CaseSvc->>CaseSvc: Compute streaming SHA-256 checksum
    CaseSvc->>Database: Persist evidence item (AVAILABLE)
    CaseSvc->>Kafka: Publish EvidenceIngestedEvent (topic: evidence-intake-events)
    CaseSvc-->>Investigator: HTTP 201 Created (Evidence ID, Checksum)
    
    Note over Kafka,ForensicSvc: Decoupled Asynchronous Processing
    Kafka->>ForensicSvc: Consume EvidenceIngestedEvent
    ForensicSvc->>Database: Create initial Chain of Custody ledger entry (INGEST)
    ForensicSvc->>ForensicSvc: Verify baseline cryptographic signature
```

### Kafka Topic Specifications

* **Topic Name:** `evidence-intake-events`
* **Producer:** `case-evidence-service` (`EvidenceKafkaProducer`)
* **Consumer:** `forensic-vault-service` (`EvidenceKafkaConsumer`)
* **Trigger Workflow:** Dispatched immediately when physical or digital evidence is cataloged into a legal case.
* **Payload Structure:**
```json
{
  "evidenceId": 142,
  "caseId": 18,
  "evidenceName": "Samsung Galaxy S22 Physical Extraction",
  "sha256Checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "trackingCode": "EVD-2026-0814",
  "actorUsername": "examiner@veritasvault.com",
  "timestamp": "2026-11-17T10:15:30Z"
}
```

### Kafka Reliability Strategy
1. **At-Least-Once Delivery:** Producer specifies `acks=all` with automatic retries to prevent message loss.
2. **Consumer Fault Tolerance:** The consumer relies on consumer groups. If the forensic service container restarts, unacknowledged offsets are resumed from the commit log without message loss.
3. **Consumer Idempotency:** The consumer queries existing custody records by `evidence_id` and action `INGEST` before creating records, preventing duplicate entries if a network retry occurs.

---

## 4. Service Data Ownership and Entity Relationship Diagram (ERD)

Each microservice manages dedicated database tables within its bounded context to maintain loose coupling:

```
veritas_db (AWS RDS PostgreSQL)
├── auth-service Domain
│   └── users (User profiles, password hashes, roles, status)
│
├── case-evidence-service Domain
│   ├── legal_cases (Dockets, status, assigned counsel)
│   ├── evidence_items (Evidence files, SHA-256 hashes, custody state)
│   ├── exhibit_binders (Trial binders, Bates numbering index)
│   ├── binder_exhibits (Binder association entries)
│   ├── discovery_productions (Discovery bundles, access tokens)
│   └── discovery_access_logs (Opposing counsel download receipts)
│
└── forensic-vault-service Domain
    ├── chain_of_custody_logs (Immutable chronological handling ledger)
    ├── custody_reservations (Lab workstation reservations and locks)
    └── forensic_lab_reports (Tamper verification reports and findings)
```

### Visual Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    USERS ||--o{ LEGAL_CASES : "assigned lead counsel"
    USERS ||--o{ CUSTODY_RESERVATIONS : "requests reservation"
    USERS ||--o{ CHAIN_OF_CUSTODY_LOGS : "performs custody action"

    LEGAL_CASES ||--o{ EVIDENCE_ITEMS : "contains"
    LEGAL_CASES ||--o{ EXHIBIT_BINDERS : "organizes"
    LEGAL_CASES ||--o{ DISCOVERY_PRODUCTIONS : "produces"

    EXHIBIT_BINDERS ||--o{ BINDER_EXHIBITS : "includes"
    EVIDENCE_ITEMS ||--o{ BINDER_EXHIBITS : "referenced in"

    DISCOVERY_PRODUCTIONS ||--o{ DISCOVERY_ACCESS_LOGS : "logs receipts"

    EVIDENCE_ITEMS ||--o{ CHAIN_OF_CUSTODY_LOGS : "tracked by"
    EVIDENCE_ITEMS ||--o{ CUSTODY_RESERVATIONS : "reserved for lab"
    CUSTODY_RESERVATIONS ||--o| FORENSIC_LAB_REPORTS : "produces report"

    USERS {
        uuid id PK
        string email UK
        string password_hash
        string full_name
        string role
        string status
    }

    LEGAL_CASES {
        uuid id PK
        string case_number UK
        string title
        string court_jurisdiction
        string judge_name
        uuid lead_counsel_id FK
        string status
    }

    EVIDENCE_ITEMS {
        uuid id PK
        uuid case_id FK
        string evidence_number UK
        string bates_number UK
        string file_name
        string sha256_checksum
        bigint file_size_bytes
        string status
    }

    EXHIBIT_BINDERS {
        uuid id PK
        uuid case_id FK
        string title
        string status
    }

    BINDER_EXHIBITS {
        uuid id PK
        uuid binder_id FK
        uuid evidence_item_id FK
        string exhibit_number
        int order_index
    }

    DISCOVERY_PRODUCTIONS {
        uuid id PK
        uuid case_id FK
        string production_number UK
        string opposing_counsel_email
        string access_token UK
        string status
    }

    DISCOVERY_ACCESS_LOGS {
        uuid id PK
        uuid production_id FK
        string ip_address
        timestamp accessed_at
    }

    CUSTODY_RESERVATIONS {
        uuid id PK
        uuid evidence_item_id FK
        uuid requested_by_user_id FK
        string purpose
        string status
        timestamp start_date
        timestamp end_date
    }

    FORENSIC_LAB_REPORTS {
        uuid id PK
        uuid reservation_id FK
        uuid analyst_id FK
        string forensic_tool_name
        string tool_version
        string integrity_status
        text findings
    }

    CHAIN_OF_CUSTODY_LOGS {
        uuid id PK
        uuid evidence_item_id FK
        uuid actor_user_id FK
        string action_type
        boolean tamper_flag
        timestamp recorded_at
    }
```

---

## 5. Caching and Interactive API Documentation

### Spring Caching Strategy
To reduce database query load for frequently requested, slow changing entities, Spring Cache (`@EnableCaching`) is enabled:
* **`AdminUserService`:** Cached with `@Cacheable(value = "users", key = "#id")`. Evicted upon user updates with `@CacheEvict(value = "users", key = "#id")`.
* **`LegalCaseService`:** Case lookups cached with `@Cacheable(value = "cases", key = "#id")` and invalidated when case status or dockets are modified.

### Interactive OpenAPI / Swagger UI
Every service includes `springdoc-openapi-starter-webmvc-ui` (v2.6.0). Documentation is accessible directly via the API Gateway or individual service ports:
* **Auth Service Docs:** `http://98.91.232.127:8081/swagger-ui/index.html`
* **Case and Evidence Docs:** `http://98.91.232.127:8082/swagger-ui/index.html`
* **Forensic Vault Docs:** `http://98.91.232.127:8083/swagger-ui/index.html`

---

## 6. Automated Testing (Cucumber BDD)

Each microservice contains Behavior Driven Development (BDD) test suites written in Gherkin feature files with Cucumber steps.

* Feature files location: `src/test/resources/features/`
* Step definitions location: `src/test/java/.../steps/`

To run the automated test suites:
```bash
# Run tests in Auth Service
mvn test -f auth-service/pom.xml

# Run tests in Case & Evidence Service
mvn test -f case-evidence-service/pom.xml

# Run tests in Forensic Vault Service
mvn test -f forensic-vault-service/pom.xml
```

All Cucumber scenarios pass with zero failures and zero errors before code can be packaged or deployed.

---

## 7. Cloud Deployment (AWS RDS + EC2 + ECR)

The live production system is hosted on Amazon Web Services in region `us-east-1` (N. Virginia):

* **AWS RDS (PostgreSQL 16):** Database identifier `veritasvault-db.ckf0yi44iig0.us-east-1.rds.amazonaws.com`.
  * Secured via Security Group Chaining (`sg-02c010939a6dc9258`) restricted to traffic originating from the EC2 security group (`sg-09179e300ebb6bed3`).
* **AWS EC2 (Ubuntu 24.04 LTS):** Instance identifier `i-0058f806ef1591c17` (`t3.medium`).
  * Public IP: `98.91.232.127`
  * Attached IAM Role: `EC2-ECR-Role` with `AmazonEC2ContainerRegistryPowerUser` policy.
* **AWS ECR (Elastic Container Registry):** 5 private container repositories under account `411711548396`:
  1. `411711548396.dkr.ecr.us-east-1.amazonaws.com/veritas-auth-service`
  2. `411711548396.dkr.ecr.us-east-1.amazonaws.com/veritas-case-evidence-service`
  3. `411711548396.dkr.ecr.us-east-1.amazonaws.com/veritas-forensic-vault-service`
  4. `411711548396.dkr.ecr.us-east-1.amazonaws.com/veritas-api-gateway`
  5. `411711548396.dkr.ecr.us-east-1.amazonaws.com/veritas-frontend`
  * Tagged with semantic version `1.0.0` and `latest`.

### Live Production Web Application
* **Frontend Web Application:** [http://98.91.232.127:5173](http://98.91.232.127:5173)
* **API Gateway Route:** [http://98.91.232.127:8080/api](http://98.91.232.127:8080/api)

---

## 8. Continuous Integration and Continuous Deployment (Jenkins CI/CD)

The CI/CD pipeline is fully defined as code in the project root [Jenkinsfile](Jenkinsfile) and executed via Jenkins Declarative Pipeline syntax.

```
Git Push to GitHub
       │
       ▼
[Stage 1: Checkout] ➔ Clones source branch from GitHub
       │
       ▼
[Stage 2: Automated BDD Testing] ➔ Runs Cucumber test suites across microservices (mvn test)
       │
       ▼
[Stage 3: Package Application] ➔ Builds executable Spring Boot JARs and Vite production bundle
       │
       ▼
[Stage 4: Build Docker Images] ➔ Validates container images for all 5 services
       │
       ▼
[Stage 5: Push Images to AWS ECR] ➔ Authenticates via aws-credentials and pushes versioned tags
       │
       ▼
[Stage 6: Continuous Deployment to AWS EC2] ➔ Triggers EC2 pull and container recreate
       │
       ▼
Finished: SUCCESS (All stages green in Jenkins Blue Ocean)
```

### Automated Trigger (Poll SCM)
The pipeline is configured with automated SCM polling:
* Schedule: `H/5 * * * *` (Checks GitHub every 5 minutes for new commits).
* Execution Mode: When code is pushed, Jenkins automatically triggers, tests the code, verifies the build, and prepares deployment without manual intervention.

---

## 9. Running the Application Locally

Anyone cloning this repository can launch the entire distributed stack locally with a single command. No local installations of Java, Maven, PostgreSQL, or Kafka are required.

### Prerequisites
* Docker Desktop 24+ and Docker Compose v2+ installed.

### Start All Services
```bash
# Clone the repository
git clone https://github.com/Nightowl-ops/THELaw.git
cd THELaw

# Launch PostgreSQL, Kafka, all 4 Spring Boot microservices, and React Frontend
docker compose up -d
```

### Access Points
* React Frontend: `http://localhost:5173`
* API Gateway: `http://localhost:8080`
* Auth Service: `http://localhost:8081`
* Case and Evidence Service: `http://localhost:8082`
* Forensic Vault Service: `http://localhost:8083`
* pgAdmin 4: `http://localhost:5050` (Login: `admin@veritasvault.com` / `adminpassword`)

### Default Test Credentials
* Admin: `admin@veritasvault.com` / `Password123!`
* Forensic Examiner: `examiner@veritasvault.com` / `Password123!`
* Attorney: `attorney@veritasvault.com` / `Password123!`
* Client: `client@veritasvault.com` / `Password123!`

---

## 10. Environment Variables Reference

| Variable Name | Description | Default Local Value | Production AWS Value |
| :--- | :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | JDBC Database Connection URL | `jdbc:postgresql://postgres:5432/veritas_db` | `jdbc:postgresql://veritasvault-db.ckf0yi44iig0.us-east-1.rds.amazonaws.com:5432/veritas_db` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgrespassword` | Configured securely via AWS environment |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker endpoint | `kafka:9092` | `kafka:9092` |
| `AUTH_SERVICE_URL` | Route target for Auth service | `http://auth-service:8081` | `http://auth-service:8081` |
| `CASE_SERVICE_URL` | Route target for Case service | `http://case-evidence-service:8082` | `http://case-evidence-service:8082` |
| `FORENSIC_SERVICE_URL` | Route target for Forensic service | `http://forensic-vault-service:8083` | `http://forensic-vault-service:8083` |
| `APP_STORAGE_UPLOAD_DIR` | Forensic disk storage volume | `/app/uploads` | `/app/uploads` |

---

## 11. Project Summary and Unit 4 Deliverables

This project fulfills all criteria of the Unit 4 Productionization Project:
1. **Continuation of Prior Projects:** Retained all business logic, users, cases, evidence hashing, Bates stamping, and custody workflows.
2. **Microservices Decomposition:** Refactored into 4 independent Spring Boot services + API Gateway with clear bounded contexts.
3. **Containerization:** Complete Dockerfiles and multi-container Docker Compose orchestration for zero-dependency execution.
4. **Asynchronous Messaging:** Integrated Apache Kafka for event-driven evidence processing and auditing.
5. **Caching:** Spring Caching implemented on core read paths.
6. **Cloud Deployment:** Live in production on AWS (RDS PostgreSQL, EC2, and private ECR container registry).
7. **CI/CD Automation:** Declarative Jenkins pipeline automating testing, packaging, and deployment verification.
