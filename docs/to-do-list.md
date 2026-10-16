##  7-Day Agile Development Plan & Roadmap

<div align="center">

|  Duration  |  Git Branches |  Domain Entities |     Security Model     |  Sprint Status |
|:----------:| :---: |:------------------:|:------------------------:| :---: |
| **7 Days** | **9 Feature Branches** |  **9 JPA Tables**  | **Stateless JWT (RBAC)** | ![Completed](https://img.shields.io/badge/Sprint-100%25%20Completed-brightgreen?style=flat-square) |

</div>

###  Visual Execution Pipeline

```mermaid
flowchart LR
    D1["Day 1<br/><b>Architecture & ERD</b>"] --> D2["Day 2<br/><b>JPA Entities & SQL</b>"]
    D2 --> D3["Day 3<br/><b>Spring Security & JWT</b>"]
    D3 --> D4["Day 4<br/><b>DTOs & Case APIs</b>"]
    D4 --> D5["Day 5<br/><b>SHA-256 Hashing Vault</b>"]
    D5 --> D6["Day 6<br/><b>Collision Engine & SSE</b>"]
    D6 --> D7["Day 7<br/><b>Swagger & Seeding</b>"]

    style D1 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D2 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D3 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D4 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D5 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D6 fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#fff
    style D7 fill:#064e3b,stroke:#10b981,stroke-width:3px,color:#fff