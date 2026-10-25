# VeritasVault - REST API Reference

* Base Server URL: `http://localhost:8085`
* Interactive Swagger Documentation: [http://localhost:8085/swagger-ui/index.html](http://localhost:8085/swagger-ui/index.html)
* OpenAPI JSON Specification: [http://localhost:8085/v3/api-docs](http://localhost:8085/v3/api-docs)

---

### 1. Authentication & User Management
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Self-register as new client user | Public |
| `GET` | `/api/auth/verify?token={token}` | Confirm email using 24-hr token | Public |
| `POST` | `/api/auth/login` | Authenticate and obtain JWT Bearer token | Public |
| `POST` | `/api/auth/forgot-password` | Request password reset email | Public |
| `POST` | `/api/auth/reset-password` | Reset password using 1-hr token | Public |
| `POST` | `/api/auth/change-password` | Update password for current user | Authenticated |
| `GET` | `/api/auth/me` | Fetch authenticated user profile | Authenticated |
| `PUT` | `/api/auth/profile` | Update contact and professional credentials | Authenticated |
| `POST` | `/api/auth/profile/picture` | Upload profile avatar (PNG/JPEG) | Authenticated |
| `GET` | `/api/auth/profile/picture/{userId}` | Stream user profile picture resource | Authenticated |

---

### 2. Administrator Operations
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | Paginated search of all vault users | `ROLE_ADMIN` |
| `POST` | `/api/admin/users` | Provision privileged account (`ATTORNEY`, `EXAMINER`, `ADMIN`) | `ROLE_ADMIN` |
| `PATCH` | `/api/admin/users/{id}/status` | Activate or suspend user account | `ROLE_ADMIN` |
| `PATCH` | `/api/admin/users/{id}/role` | Modify user authorization role | `ROLE_ADMIN` |

---

### 3. Legal Cases
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/cases` | Paginated case search (client-scoped or firm-wide) | Authenticated |
| `POST` | `/api/cases` | Open a new legal case docket | `ROLE_ATTORNEY`, `ROLE_ADMIN` |
| `GET` | `/api/cases/{caseId}` | Fetch case details by ID | Authenticated |
| `PATCH` | `/api/cases/{caseId}/status` | Update lifecycle (`ACTIVE`, `PENDING_TRIAL`, `CLOSED`) | `ROLE_ATTORNEY`, `ROLE_ADMIN` |

---

### 4. Evidence Items & Forensic Vault
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/evidence/upload` | Ingest evidence file with SHA-256 calculation | Authenticated |
| `GET` | `/api/evidence/{evidenceId}` | Fetch evidence metadata by numeric ID | Authenticated |
| `GET` | `/api/evidence/tracking/{code}` | Fetch evidence by tracking code (`EV-YYYY-XXXX`) | Authenticated |
| `GET` | `/api/evidence/case/{caseId}` | Paginated list of evidence in a case | Authenticated |
| `GET` | `/api/evidence/{evidenceId}/download`| Stream raw evidence binary from storage | Authenticated |
| `PATCH` | `/api/evidence/{evidenceId}/status` | Move evidence across legal finite-state machine | Authenticated |
| `POST` | `/api/evidence/{evidenceId}/simulate-tamper` | Ingest corrupting byte for tamper demo | `ROLE_ADMIN`, `ROLE_FORENSIC_EXAMINER` |

---

### 5. Chain of Custody (Immutable Ledger)
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/custody/logs/evidence/{evidenceId}` | Get chronological, append-only custody timeline | Authenticated |

---

### 6. Custody Reservations & Multithreaded Lab Check-In
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/custody/reservations` | Reserve lab slot with pessimistic DB lock | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` |
| `GET` | `/api/custody/reservations/{id}` | Get reservation details | Authenticated |
| `GET` | `/api/custody/reservations/evidence/{evidenceId}` | Get reservation schedule for evidence | Authenticated |
| `GET` | `/api/custody/reservations/examiner/{examinerId}` | Get schedule for examiner | Authenticated |
| `POST` | `/api/custody/reservations/{id}/checkout` | Check out evidence to bench (`IN_LAB`) | Assigned Examiner / Admin |
| `POST` | `/api/custody/reservations/{id}/checkin` | **Unit 3**: Multithreaded SHA-256 verification & lab report | Assigned Examiner / Admin |
| `GET` | `/api/custody/reservations/{id}/report` | Fetch 10th table forensic verification report | Authenticated |
| `POST` | `/api/custody/reservations/{id}/cancel` | Cancel booking and release slot | Assigned Examiner / Admin |

---

### 7. Exhibit Binders & Bates Numbering
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/binders/case/{caseId}` | Fetch all binders for a case | Authenticated |
| `POST` | `/api/binders` | Create new trial binder | `ROLE_ATTORNEY`, `ROLE_ADMIN` |
| `GET` | `/api/binders/{binderId}` | Get binder with sequentially numbered exhibits | Authenticated |
| `PATCH` | `/api/binders/{binderId}/status` | Transition status (`DRAFT`, `FINALIZED`, `SERVED`) | `ROLE_ATTORNEY`, `ROLE_ADMIN` |
| `POST` | `/api/binders/{binderId}/exhibits` | Add exhibit with continuous Bates numbering | `ROLE_ATTORNEY`, `ROLE_ADMIN` |
| `DELETE` | `/api/binders/{binderId}/exhibits/{exhibitId}` | Remove exhibit and trigger re-indexing cascade | `ROLE_ATTORNEY`, `ROLE_ADMIN` |

---

### 8. Discovery Productions & Access Receipts
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/discovery/produce` | Serve binder, hash UUID token, and lock binder | `ROLE_ATTORNEY`, `ROLE_ADMIN` |
| `GET` | `/api/discovery/view/{token}` | Opposing counsel landing page preview | Public (Valid Token) |
| `GET` | `/api/discovery/download/{token}` | Download discovery bundle and log IP receipt | Public (Valid Token) |
| `GET` | `/api/discovery/{productionId}/receipts` | Fetch certified Proof of Service audit logs | `ROLE_ATTORNEY`, `ROLE_ADMIN` |

---

### 9. Real-Time Telemetry Streaming
| Method | Endpoint | Description | Access Tier |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications/subscribe` | Open persistent Server-Sent Events (SSE) telemetry stream | Authenticated |