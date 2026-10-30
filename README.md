# QUANTUM-LOCK

> **Zero-Trust, High-Assurance Multi-Tenant Document Security Platform**

QUANTUM-LOCK is an enterprise-grade document repository and security platform engineered around cryptographic zero-trust principles. Built on **Spring Boot 3.3.x (Java 21)** and **React 18 + TypeScript + Tailwind CSS**, it features envelope encryption, strict tenant isolation, blind keyword search, cryptographic audit hash chains, idempotent operations, and atomic recovery.

---

## Key Security Guarantees

1. **Envelope Encryption**: Files are encrypted with random 256-bit AES-GCM data encryption keys (DEKs). DEKs are wrapped using AESWrap under tenant-isolated Key Encryption Keys (KEKs) managed by a KMS. DEK byte arrays are immediately zeroized in memory.
2. **Absolute Multi-Tenancy**: The tenant identifier is NEVER accepted from user input or request bodies. Tenant scope is derived strictly from verified JWT claims on the server.
3. **In-Memory JWT Auth**: The frontend stores JWTs strictly in JavaScript memory (no `localStorage` or `sessionStorage`), ensuring token leakage protection against XSS.
4. **Zero-Knowledge Blind Search**: Search tokens are indexed using HMAC-SHA256 with tenant-isolated salt. Document contents are never stored in plaintext on the database or search index.
5. **Cryptographic Audit Hash Chain**: Every security-relevant event forms a tamper-evident SHA-256 hash chain serialized per tenant. The chain can be mathematically verified on demand, immediately pinning any tampered record.
6. **Strict Idempotency**: All mutating operations (`upload`, `share`, `rotate`) enforce a unique `Idempotency-Key` header with SHA-256 payload digest verification.
7. **Optimistic Concurrency Policies**: Dynamic ABAC policies support optimistic locking with instant `409 Conflict` detection and client-side resolution banners.
8. **Asynchronous Key Rotation**: KEK version upgrades trigger cursor-paginated background re-wrapping of DEKs with real-time UI progress bars.
9. **Atomic Recovery Reconciler**: Compares database document states with MinIO object storage, completing verified uploads and cleaning orphaned ciphertexts.

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Flyway, JJWT, Resilience4j, AWS SDK v2 (S3/MinIO), RabbitMQ, Redis, PostgreSQL.
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, Lucide React, TanStack Query, Vitest.
- **Infrastructure**: Docker Compose, Nginx reverse proxy, PostgreSQL 15, Redis 7, MinIO, RabbitMQ 3.

---

## Seeded Demo Users

All demo users share the default password: **`password123`**

| Tenant | Username | Role | Department | Description |
|---|---|---|---|---|
| `alpha` | `admin` | `ROLE_ADMIN` | `SECURITY` | Full administrative control for Tenant Alpha |
| `alpha` | `user1` | `ROLE_USER` | `ENGINEERING` | Document owner & sharing user in Tenant Alpha |
| `alpha` | `user2` | `ROLE_USER` | `MARKETING` | Recipient user for cross-department grants |
| `alpha` | `auditor`| `ROLE_AUDITOR` | `COMPLIANCE` | Read-only compliance & audit chain verification |
| `beta` | `admin` | `ROLE_ADMIN` | `SECURITY` | Isolated Administrator for Tenant Beta |
| `beta` | `user1` | `ROLE_USER` | `SALES` | Isolated User for Tenant Beta |
| `gamma` | `admin` | `ROLE_ADMIN` | `SECURITY` | Isolated Administrator for Tenant Gamma |

*(Quick-fill buttons for these seeded users are built directly into the login screen!)*

---

## Running with Docker Compose (Recommended)

To run the entire system including backend, database, object store, message broker, cache, and frontend:

```bash
docker compose up --build
```

Services will start:
- **Frontend Web UI**: [http://localhost:3000](http://localhost:3000)
- **Backend API**: [http://localhost:8080](http://localhost:8080)
- **MinIO Console**: [http://localhost:9001](http://localhost:9001) (`minioadmin` / `minioadmin`)
- **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (`guest` / `guest`)

---

## Running Locally for Development

### 1. Prerequisites
- **Java 21+** (e.g. Eclipse Adoptium Temurin 21)
- **Node.js 20+** and **npm**
- **Docker** (for backing services: Postgres, Redis, MinIO, RabbitMQ)

### 2. Start Backing Infrastructure
```bash
docker compose up postgres redis minio rabbitmq -d
```

### 3. Run Backend (Spring Boot)
```bash
# On Windows
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"  # or your JDK path
.\mvnw.cmd spring-boot:run

# On Linux / macOS
./mvnw spring-boot:run
```
The backend server runs on `http://localhost:8080`.

### 4. Run Frontend (Vite Dev Server)
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173). Requests to `/api/*` are automatically proxied to `http://localhost:8080`.

---

## Running Tests

### Backend Unit & Slice Tests (52 tests)
```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```
Tests cover:
- Envelope encryption, zeroization, nonce uniqueness, tampering detection
- Tenant isolation & unauthorized cross-tenant query rejections
- Idempotency with concurrent replay races
- Audit hash-chain generation, tamper detection & verify endpoint
- Access grant creation, quota deduction, and revocation
- Blind search token matching
- Optimistic locking for dynamic ABAC policies
- KEK rotation re-wrapping

### Frontend Unit Tests (Vitest)
```bash
cd frontend
npm run test
```
Frontend tests verify:
- RFC 9457 Problem Details error translation (401, 403, 404, 409, 429)
- Auto-injection of UUID `Idempotency-Key` headers on mutating requests
- Preservation of idempotency keys across retries
- Enforcement that no `tenant` or `tenantId` parameter is sent by the client

### Frontend Production Build
```bash
cd frontend
npm run build
```

---

## Web UI Pages (11 Pages)

| Page | Role | Features |
|---|---|---|
| **Login** | Public | Split-screen warm design, quick-fill chips for seeded users (`alpha`, `beta`, `gamma`) |
| **Dashboard** | All | Real-time stat cards, system health pills, recent documents, live audit indicators |
| **Documents** | USER / ADMIN | Drag-and-drop envelope-encrypted upload, classification badges, version inspection drawer |
| **Search** | All | Blind index keyword search chips, exact token intersection, zero-knowledge explanation |
| **Sharing** | USER / ADMIN | Create time-bound & quota-limited access grants, remaining download counters, instant revocation |
| **Key Management** | ADMIN | Active KEK version display, re-wrap trigger, live polling progress bar (`/keys/rotations/{id}`) |
| **Policies** | ADMIN | ABAC policy rule editor with versioning and visual `409 Conflict` resolution banner |
| **Audit Log** | ADMIN / AUDITOR | Tamper-evident hash chain viewer, one-click `Verify Chain` with red highlight on tampered rows |
| **Recovery** | ADMIN | Stale upload reconciler (`/admin/reconcile`), orphan cipher cleanup, state machine timeline |
| **CIPHERSTORM** | ADMIN | Automated security report viewer, PASS/FAIL checklist cards, evidence inspector |
| **Security Proof** | All | 9-step interactive demo runner (Login → Upload → Search → Share → Download → Revoke → Rotate → Tamper → Recover) + Q01-Q15 guarantee status matrix |

---

## Project Structure

```text
quantum-lock/
├── src/main/java/com/quantumlock/
│   ├── audit/         # Audit event generation, hash chain, verify endpoint
│   ├── auth/          # JWT, SecurityConfig, RateLimiting, ProblemDetails
│   ├── common/        # CorrelationIdFilter, TenantContext, SecurityUtils
│   ├── crypto/        # EnvelopeEncryptionService, KMS, DEK zeroization
│   ├── document/      # Document & Version entities, upload state machine, download
│   ├── idempotency/   # IdempotencyFilter, IdempotencyKey entity & service
│   ├── outbox/        # Transactional outbox relay with FOR UPDATE SKIP LOCKED
│   ├── policy/        # Dynamic ABAC policies with optimistic locking
│   ├── recovery/      # Reconciler service for orphaned ciphertexts & stale states
│   ├── rotation/      # Asynchronous cursor-based KEK re-wrapping service
│   ├── search/        # HMAC-SHA256 zero-knowledge blind indexing & query
│   └── sharing/       # Time-bound, quota-tracked access grants & revocation
├── src/main/resources/
│   ├── db/migration/  # Flyway schema V1..V4
│   └── application.yml
├── frontend/
│   ├── src/
│   │   ├── api/       # Typed REST client with RFC 9457 error translation
│   │   ├── components/# Design system (Button, Card, Badge, Dialog, Drawer, Skeleton)
│   │   ├── context/   # In-memory AuthContext (JWT claim parsing)
│   │   ├── layout/    # Espresso sidebar, TopBar with tenant badge, Footer chip
│   │   ├── pages/     # 11 complete application pages
│   │   └── utils/     # Idempotency key generator
│   ├── Dockerfile     # Multi-stage build (Node 20 -> Nginx)
│   └── nginx.conf     # Nginx SPA router + reverse proxy for /api/
├── docs/
│   ├── openapi.yaml   # Full OpenAPI 3.0 specification
│   ├── test-report.json # Q01-Q15 verification test matrix
│   └── PROGRESS.md    # Implementation audit and phase progress
└── docker-compose.yml # Complete container topology
```

