# QUANTUM-LOCK Implementation Decisions

## ADR-001: Explicit Tenant Identification at Authentication
- **Status:** Accepted
- **Context:** When authenticating a user via `/api/v1/auth/login`, multiple tenants might theoretically register users with identical usernames (or distinct tenant namespaces). The system must never cross tenant boundaries or guess tenants based on global username lookups.
- **Decision:** Require `tenant` (either tenant ID UUID or tenant unique name) in the login request payload. The authentication service resolves the tenant explicitly and authenticates the user strictly within that tenant boundary.
- **Consequences:** Prevents ambiguity, tenant leakage, and timing attacks across different tenants. Client cannot spoof tenant in subsequent requests because the tenant ID is securely embedded in the signed JWT claims and extracted server-side into `TenantContext`.

## ADR-002: Envelope Encryption with JCA Standard Algorithms Only
- **Status:** Accepted
- **Context:** Data at rest in object storage requires tenant-isolated envelope encryption. Cryptographic algorithms must conform strictly to JCA standard algorithms.
- **Decision:** Use AES-256-GCM (`AES/GCM/NoPadding`) for document encryption with a random 256-bit DEK generated per document version and 96-bit random nonce (`SecureRandom.getInstanceStrong()`). Additional Authenticated Data (AAD) binds the ciphertext to `tenantId|documentId|version`. DEKs are wrapped using `AESWrap` with a Key Encryption Key (KEK) managed by KMS. Plaintext DEK bytes are zeroed immediately after wrapping/unwrapping. Ciphertext integrity is additionally verified with SHA-256 digest checks.
- **Consequences:** Guarantees cryptographic binding to tenant, document, and version, preventing ciphertext reuse or relocation attacks across tenants or versions.

## ADR-003: Multi-tenant Isolation via TenantContext and TenantAware Repositories
- **Status:** Accepted
- **Context:** Applications must prevent accidental cross-tenant data leakage at the persistence layer.
- **Decision:** Client-supplied tenant IDs in request paths or headers are never trusted for authorization. The tenant ID is extracted from validated JWT claims by `JwtAuthenticationFilter` and stored in a request-scoped `TenantContext` (ThreadLocal). All repository queries filter by `tenant_id`.
- **Consequences:** Eliminates IDOR and cross-tenant data access vulnerabilities.

## ADR-004: Two-Phase Commit State Machine for Document Upload
- **Status:** Accepted
- **Context:** Uploading encrypted payloads to object storage (MinIO) and updating relational databases requires resilience against partial network or storage failures without leaving orphaned or uncommitted documents visible to users.
- **Decision:** Implement an upload state machine: `PENDING` -> `OBJECT_STORED` -> `COMMITTED`, with failure paths leading to `FAILED`. In a single atomic database transaction, the document version transitions to `COMMITTED`, the transactional outbox event is enqueued, and the audit event is persisted. Stale operations in `PENDING` or `OBJECT_STORED` older than 5 minutes can be reconciled or cleaned up.
- **Consequences:** Ensures consistency between MinIO object storage and PostgreSQL metadata.

## ADR-005: Outbox Pattern with RabbitMQ Relay
- **Status:** Accepted
- **Context:** Domain events must be published reliably without dual-write inconsistency between the DB and the message broker.
- **Decision:** Events are written to the `outbox` table in the same transaction as the business entity changes. A scheduled `OutboxRelay` polls pending outbox entries, publishes them to RabbitMQ with deduplication and idempotency safeguards, and marks them `DELIVERED`.
- **Consequences:** At-least-once delivery with high reliability and zero distributed transactions required.
