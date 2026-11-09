# QUANTUM-LOCK: Complete Project, Architecture & Implementation Manual

---

## 1. Executive Summary: What We Built

**QUANTUM-LOCK** is an enterprise-grade, high-assurance, **Zero-Trust Multi-Tenant Document Security Platform**. It guarantees that sensitive corporate documents remain confidential and tamper-evident across their entire lifecycle — at rest, in transit, during search, and across organizational boundaries.

### Core Problems Solved:
1. **The Cloud Storage Blind Trust Problem:** In traditional cloud systems, sysadmins or cloud operators can read stored files. In QUANTUM-LOCK, documents are envelope-encrypted with unique AES-256-GCM data keys before touching the storage bucket. Storage operators only ever see encrypted ciphertexts.
2. **Cross-Tenant Data Leakage:** In multi-tenant SaaS, accidental software bugs can expose Organization A's files to Organization B. QUANTUM-LOCK enforces **pessimistic multi-tenant isolation** at the database, memory, and cryptographic levels.
3. **Audit Trail Tampering:** Typical system logs can be edited or wiped by malicious administrators. QUANTUM-LOCK maintains a **cryptographic SHA-256 hash-chained audit ledger** where each event binds to the hash of the preceding event, creating a tamper-evident blockchain-like chain.
4. **Uncontrolled Organization Onboarding:** Organizations cannot self-register into arbitrary tenants. A formal **Credential Request & Approval Workflow** prevents name duplicates and requires the **Platform Superadmin** to authorize and provision isolated workspaces.

---

## 2. What Exactly Did We Do & How Did We Implement It?

We implemented the platform in progressive, mathematically verified engineering phases:

### Phase 0: Security Foundation & Infrastructure
* **Correlation Tracking:** Implemented `CorrelationIdFilter` (`X-Correlation-ID`) tracking every request end-to-end across frontend, backend, outbox, and audit logs.
* **RFC 7807 Problem Details:** Configured `ProblemDetailsExceptionHandler` ensuring all error responses return uniform RFC 7807 problem json with no stack trace leakage.
* **Pessimistic Rate Limiting:** Built in-memory token-bucket rate limiter (`RateLimitingFilter`) enforcing limits per IP/Tenant (e.g., 30 login attempts/min, 100 API calls/min).

### Phase 1: Explicit Multi-Tenant Authentication & Identity
* **Explicit Tenant Login:** Unlike standard apps where users log in with just username/password, users must specify their `tenant`. The backend resolves the tenant boundary explicitly before checking credentials, preventing cross-tenant username collision attacks.
* **Stateless JWT with Zero-Trust Claims:** Tokens carry `sub`, `tenant`, `roles`, `department`, and `device_trust`.
* **Zero Token Persistence:** The React frontend stores JWTs **strictly in memory** (`AuthContext.tsx`). Tokens are never written to `localStorage` or `sessionStorage` (protecting against XSS token exfiltration).

### Phase 2: Envelope Encryption & Key Management (KMS)
* **AES-256-GCM Envelope Encryption:**
  1. For every document version, the system generates a cryptographically random 256-bit **Data Encryption Key (DEK)** and a 96-bit nonce.
  2. The document payload is encrypted using `AES/GCM/NoPadding`.
  3. **Additional Authenticated Data (AAD):** Bound to `tenantId|documentId|version` to prevent ciphertext relocation attacks.
  4. The DEK is wrapped using `AESWrap` with a KMS **Key Encryption Key (KEK)**.
  5. The plaintext DEK in Java memory is immediately **zeroed out** (`Arrays.fill(dek, (byte) 0)`).

### Phase 3: Resilient Two-Phase Upload & Hash-Chained Audit
* **Two-Phase Upload State Machine:**
  * Transitions: `PENDING` $\rightarrow$ `OBJECT_STORED` (in MinIO) $\rightarrow$ `COMMITTED` (in PostgreSQL).
  * If MinIO or the network fails, the state transitions to `FAILED`. A background reconciliation worker cleans up orphaned objects.
* **Mandatory Idempotency:** All mutating requests require an `Idempotency-Key` header with payload hash validation.
* **Tamper-Evident SHA-256 Audit Trail:**
  * Every tenant maintains a strictly monotonic sequence where:
    $$\text{event\_hash} = \text{SHA-256}(\text{chain\_position} \parallel \text{tenant\_id} \parallel \text{actor} \parallel \text{action} \parallel \text{resource} \parallel \text{outcome} \parallel \text{previous\_hash})$$
  * Any database alteration breaks the chain immediately, flagged by `POST /api/v1/audit/verify`.
* **Transactional Outbox Relay:** Business operations and domain events write atomically to PostgreSQL in the same transaction. A scheduled worker reads the `outbox` table using `SELECT ... FOR UPDATE SKIP LOCKED` and relays messages to RabbitMQ.

### Phase 4: Quota-Enforced Sharing & Linearized Revocation
* Users can share documents with time limits and maximum download quotas.
* Every download atomically decrements the quota. When exhausted or revoked, subsequent attempts immediately return `HTTP 410 GONE` or `HTTP 403 FORBIDDEN`.

### Phase 5: Automated Key Rotation
* Re-wraps stored DEKs from old KEK versions to new KEK versions in batches without ever decrypting or touching the underlying plaintext document files.

### Phase 6: Searchable Encryption (HMAC-SHA256 Blind Index)
* Enables searching documents without storing or exposing plaintext search keywords.
* Search terms are normalized and hashed with a tenant-isolated HMAC secret (`HMAC-SHA256`). Searches query the blind index hashes directly.

### Phase 7: Credential Onboarding & SMTP Notification Workflow
* **Redundancy Checker:** Live API (`GET /api/v1/auth/check-tenant?name=...`) checks PostgreSQL and pending requests to guarantee tenant name uniqueness.
* **Request Submission:** Prospective clients fill out their details on `/request-credentials`.
* **Dual-Delivery Email Engine:** Alerts the platform owner with deep-link buttons (`ACCEPT & PROVISION` and `REJECT`). If SMTP keys are missing, emails are stored safely in PostgreSQL (`email_notification` table) so nothing is lost.
* **Automated Provisioning:** Approval automatically registers the isolated tenant, creates the tenant admin user with `HIGH` device trust, and emails initial login credentials.

### Phase 8: Platform Superadmin Governance & Cross-Tenant Access Control
* **Architectural Separation:** Regular tenant admins (`tenant-alpha/admin`, `tenant-beta/admin`) are restricted to their own tenant and strictly blocked (`HTTP 403 Forbidden`) from viewing or approving other companies' requests.
* **Platform Superadmin:** A governance role (`SUPER_ADMIN`) exists inside the `platform` tenant (`00000000-0000-0000-0000-000000000001`) with exclusive rights to manage onboarding.

---

## 3. Complete Inventory of Tools & Technologies Used

Every tool, library, and system component used in QUANTUM-LOCK is detailed below:

### Backend (Java & Spring Ecosystem)
| Tool / Library | Version | Purpose |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | 21 (Eclipse Adoptium) | Modern LTS Java runtime with virtual threads and enhanced cryptography. |
| **Spring Boot** | 3.3.6 | Core backend framework for DI, REST APIs, and application lifecycle. |
| **Spring Security** | 6.x | Stateless JWT authentication, role-based `@PreAuthorize`, method security. |
| **Spring Data JPA / Hibernate** | 6.5.x | Object-relational mapping, PostgreSQL dialect, and optimistic locking. |
| **Flyway** | 10.x | Version-controlled database schema migrations (`V1` through `V6`). |
| **Spring Boot Starter Mail** | 3.3.6 | SMTP email delivery engine with Jakarta Mail & JavaMailSender. |
| **Resilience4j** | 2.2.0 | Circuit breaker, retry mechanisms, and fault tolerance for external services. |
| **AWS Java SDK v2 (S3 Client)** | 2.25.x | High-performance client used to communicate with MinIO S3 object storage. |
| **jjwt (Java JWT)** | 0.12.5 | JWT token generation, HMAC-SHA256 signing, and claims parsing. |
| **Springdoc OpenAPI / Swagger UI**| 2.5.0 | Interactive API documentation at `/swagger-ui.html`. |
| **Lombok** | 1.18.x | Boilerplate reduction for models, builders, and loggers. |
| **Jackson JSR-310** | 2.17.x | Java 8 Date/Time (`Instant`) serialization and deserialization module. |

### Frontend (Modern TypeScript SPA)
| Tool / Library | Version | Purpose |
| :--- | :--- | :--- |
| **React** | 18.3.x | Component-based UI rendering engine. |
| **TypeScript** | 5.5.x | Strict type safety across all API payloads and states. |
| **Vite** | 5.4.x | Fast build tool, bundling, and local development proxy. |
| **Tailwind CSS** | 3.4.x | Utility-first styling with custom palette (espresso, terracotta, sand, cream). |
| **TanStack React Query** | 5.59.x | Async server-state management, cache invalidation, and data fetching. |
| **React Router DOM** | 6.26.x | Client-side routing with role-based `<ProtectedRoute>`. |
| **Lucide React** | 0.446.x | Modern iconography for security statuses, shields, and audit icons. |

### Data Stores, Brokers & Middleware
| Technology | Image / Version | Role in QUANTUM-LOCK |
| :--- | :--- | :--- |
| **PostgreSQL** | 16-alpine (Port 5434) | Primary ACID relational database storing metadata, audit log, outbox, and users. |
| **MinIO** | Latest (Ports 9000/9001) | S3-compatible high-performance object store holding AES-256-GCM ciphertexts. |
| **Redis** | 7-alpine (Port 6379) | High-speed cache for token blacklisting, idempotency keys, and rate limits. |
| **RabbitMQ** | 3-alpine (Ports 5672/15672) | AMQP message broker receiving reliable events via the Transactional Outbox. |
| **Nginx** | Alpine (Port 3000) | Production reverse proxy, static file server, and 50MB payload handler. |

### Testing & Verification
| Tool | Purpose |
| :--- | :--- |
| **Python 3.14** | Automated end-to-end integration test runner (`run_tests.py`, 38 checkpoints). |
| **JUnit 5 & Mockito** | Backend unit tests (`mvnw.cmd test`, 55/55 tests passing). |

---

## 4. Explaining Unfamiliar Files and Directories

If you are new to Java, Maven, or Docker projects, here is what each specific file does:

### 1. `.mvn/` and `mvnw` / `mvnw.cmd` (The Maven Wrapper)
* **What it is:** The **Maven Wrapper**.
* **Why it exists:** Maven is the build tool for Java. Normally, you would need to download and install Maven manually on your operating system. The wrapper makes the project self-contained:
  * `mvnw` (Linux/Mac script) and `mvnw.cmd` (Windows script) check if Maven is installed. If not, they download the exact required version of Maven automatically into `.mvn/wrapper/`.
  * This guarantees that any developer or server builds the project with the **exact same Maven version** without installing anything globally.

### 2. `pom.xml` (Project Object Model)
* **What it is:** The heart of any Java Maven project (equivalent to `package.json` in Node.js).
* **What is inside:**
  * **Coordinates:** Group ID (`com.quantumlock`), Artifact ID (`quantum-lock`), and Version (`1.0.0-SNAPSHOT`).
  * **Dependencies (`<dependencies>`):** Lists every library the project needs (Spring Boot, PostgreSQL driver, MinIO SDK, JJWT, etc.). Maven downloads these automatically from Maven Central.
  * **Plugins (`<build><plugins>`):** Tools that compile Java 21 code, package the executable JAR, and run unit tests.

### 3. `Dockerfile` & `docker-compose.yml`
* **`Dockerfile` (Backend):** A multi-stage recipe:
  * *Stage 1 (Build):* Runs inside a Maven + JDK container to compile the source code into `quantum-lock-1.0.0-SNAPSHOT.jar`.
  * *Stage 2 (Runtime):* Copies only the compiled JAR into a lightweight `eclipse-temurin:21-jre-alpine` image to keep the production image tiny, fast, and secure.
* **`docker-compose.yml`:** The orchestration file that boots all 6 services simultaneously with health checks, persistent volumes, and shared networking.

### 4. `frontend/nginx.conf`
* **What it is:** The production web server configuration for the React app.
* **Two primary tasks:**
  1. Serves the static compiled HTML/JS/CSS files from `/usr/share/nginx/html`.
  2. Acts as a **Reverse Proxy**: When the browser sends a request to `/api/...`, Nginx intercepts it and forwards it internally to `http://app:8080/api/...`, bypassing CORS issues.

### 5. `Flyway Migrations` (`src/main/resources/db/migration/`)
* Files named `V1__create_schema.sql`, `V2__seed_data.sql`, ..., `V6__platform_super_admin.sql`.
* **What they do:** Database version control. Every time the application starts, Flyway checks which migration files have already been run. If a new version exists (e.g. `V6`), Flyway runs it automatically. This prevents manual SQL editing errors.

### 6. `.env.example` vs `.env`
* `.env.example` is committed to Git as a public template documenting every variable required.
* `.env` is created on your specific machine or server (and ignored by Git) to store real, private production passwords and API keys.

---

## 5. Superadmin Credentials & Platform Governance

### The Governance Boundary
* **Tenant Admins:** Manage documents, users, keys, and policies *only* within their own company enclave (e.g., `tenant-alpha`, `tenant-beta`). They have **no authority** over other tenants.
* **Platform Superadmin:** Oversees platform onboarding, reviews incoming credential requests, and provisions new isolated tenants.

### Superadmin Login Credentials
| Field | Value |
| :--- | :--- |
| **Tenant / Organization** | `platform` |
| **Username** | `superadmin` |
| **Password** | `password123` |
| **Assigned Role** | `SUPER_ADMIN` |
| **Platform Tenant UUID** | `00000000-0000-0000-0000-000000000001` |
| **Superadmin User UUID** | `00000000-0000-0000-0000-000000000002` |

### How the Onboarding Workflow Operates:
1. A prospective tenant submits a request at `/request-credentials`.
2. The system validates that the tenant name is unique (preventing redundancy).
3. An alert email is dispatched to the platform owner (`chavandipak0606@gmail.com`) with interactive action links:
   * **Approve Link:** `http://localhost:5173/review-request/<id>?action=approve`
   * **Reject Link:** `http://localhost:5173/review-request/<id>?action=reject`
4. The Platform Superadmin logs in with the credentials above.
5. In the **Access Requests** queue (`/review-request`), clicking **Approve & Provision**:
   * Registers the new isolated tenant in PostgreSQL.
   * Creates the tenant's first Administrator account.
   * Automatically dispatches an email to the requester containing their initial password and 1-click login link.

---

## 6. How to Run & Verify the Platform

### Running in Development (Local)
1. **Infrastructure:** Start databases and queues:
   ```powershell
   docker compose up postgres redis minio rabbitmq -d
   ```
2. **Backend:** Run with Java 21:
   ```powershell
   $env:PlatformMail = "chavandipak0606@gmail.com"
   $env:PLATFORM_MAIL = "chavandipak0606@gmail.com"
   .\mvnw.cmd spring-boot:run
   ```
3. **Frontend:** Run with Node.js:
   ```powershell
   cd frontend
   npm run dev
   ```

### Running in Full Production (Docker)
```powershell
cp .env.example .env
docker compose up --build -d
```

### Running the 38-Point Test Suite
```powershell
python run_tests.py
```
* **Suite 1:** 13/13 Credential Onboarding, Multi-Tenant Isolation & SMTP alerts.
* **Suite 2:** 25/25 Core Zero-Trust features (Envelope Encryption, Key Rotation, Sharing Quotas, Tamper-Evident Hash Chain, ABAC Policies).
* **Result:** `38/38 VERIFICATION CHECKPOINTS 100% SUCCESSFUL`.

