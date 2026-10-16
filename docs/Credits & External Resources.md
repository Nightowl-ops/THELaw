## Credits & External Resources

### Cloud Services & External APIs
* **[Google Workspace / Gmail SMTP Relay](https://support.google.com/mail/answer/185833)**  Integrated via Spring Mail and Google App Passwords (2FA application-specific credentials) for automated outbound discovery notifications and custody alerts.

### Frameworks & Core Runtime
* **[Spring Boot](https://spring.io/projects/spring-boot)**  Core application framework, dependency injection, and RESTful web service runtime.
* **[Spring Data JPA / Hibernate](https://spring.io/projects/spring-data-jpa)**  Relational persistence, object-relational mapping (ORM), and transaction management.
* **[PostgreSQL](https://www.postgresql.org/)**  Relational database engine supporting indexed conflict queries and transaction atomicity.
* **[Project Lombok](https://projectlombok.org/)**  Java library used to eliminate boilerplate code (e.g., `@Builder`, `@RequiredArgsConstructor`, `@Getter`, `@Setter`).

### Security & Cryptography
* **Java Cryptography Architecture (`java.security.MessageDigest`)**  Native high-performance streaming cryptographic engine used for SHA-256 evidence integrity hashing.
* **[Spring Security & JJWT](https://github.com/jwtk/jjwt)**  Stateless JSON Web Token authentication, role-based authorization (RBAC), and route protection.

### Testing & API Documentation
* **[JUnit 5](https://junit.org/junit5/)** & **[Mockito](https://site.mockito.org/)**  Unit and behavioral testing framework used for mock-driven service verification and collision logic testing.
* **[Springdoc-OpenAPI / Swagger UI](https://springdoc.org/)**  Automated generation of interactive OpenAPI 3.0 API documentation and live endpoint runner.

### Real-Time & Web Communication
* **Spring Server-Sent Events (SSE)**  Reactive server-to-client event streaming used to broadcast live custody status transitions.

### Legal & Domain References
* **Bates Numbering Standards**  Legal indexing protocol utilized for sequential courtroom exhibit labeling within `exhibit_binders`.
* **Federal Rules of Evidence (FRE Rule 901 / 902)**  Legal admissibility guidelines governing self-authenticating electronic records and chain of custody preservation.
* **Landmark Custody Precedents**  Architectural requirements informed by real-world evidentiary spoliation and custody failure cases (*O.J. Simpson (1995)*, *U.S. v. Ross Ulbricht (Silk Road)*, *Qualcomm v. Broadcom*).