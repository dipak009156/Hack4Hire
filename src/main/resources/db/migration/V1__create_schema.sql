-- V1__create_schema.sql

-- tenants
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- users
CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles TEXT[] NOT NULL DEFAULT '{}',
    department VARCHAR(100),
    device_trust_level VARCHAR(50) DEFAULT 'STANDARD',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, username)
);
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);

-- policies
CREATE TABLE policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    expression TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);
CREATE INDEX idx_policy_tenant ON policy(tenant_id);

-- documents
CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    owner_id UUID NOT NULL REFERENCES app_user(id),
    name VARCHAR(1024) NOT NULL,
    classification VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    current_version INTEGER NOT NULL DEFAULT 0,
    metadata_hmac VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_document_tenant ON document(tenant_id);
CREATE INDEX idx_document_owner ON document(owner_id);

-- document versions
CREATE TABLE document_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(id),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    version INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    ciphertext_digest VARCHAR(255) NOT NULL,
    nonce VARCHAR(255) NOT NULL,
    wrapped_dek TEXT NOT NULL,
    kek_version INTEGER NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'AES-256-GCM',
    auth_tag_length INTEGER NOT NULL DEFAULT 128,
    state VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    size_bytes BIGINT,
    content_type VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, document_id, version)
);
CREATE INDEX idx_docver_document ON document_version(document_id);
CREATE INDEX idx_docver_tenant ON document_version(tenant_id);
CREATE INDEX idx_docver_state ON document_version(state);

-- access grants
CREATE TABLE access_grant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(id),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    grantor_id UUID NOT NULL REFERENCES app_user(id),
    grantee_id UUID NOT NULL REFERENCES app_user(id),
    permissions TEXT[] NOT NULL DEFAULT '{}',
    purpose VARCHAR(255),
    expires_at TIMESTAMPTZ,
    max_downloads INTEGER,
    remaining_downloads INTEGER,
    policy_version BIGINT,
    revoked_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_grant_document ON access_grant(document_id);
CREATE INDEX idx_grant_tenant ON access_grant(tenant_id);
CREATE INDEX idx_grant_grantee ON access_grant(grantee_id);

-- operations (idempotency)
CREATE TABLE operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    caller_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    payload_digest VARCHAR(255) NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    response_body TEXT,
    response_status INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, caller_id, idempotency_key, endpoint)
);
CREATE INDEX idx_operation_tenant ON operation(tenant_id);
CREATE INDEX idx_operation_key ON operation(idempotency_key);

-- outbox
CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    delivery_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_status ON outbox(delivery_status);
CREATE INDEX idx_outbox_tenant ON outbox(tenant_id);

-- audit events
CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence BIGSERIAL NOT NULL,
    tenant_id UUID NOT NULL,
    actor_id UUID,
    actor_name VARCHAR(255),
    resource_type VARCHAR(100),
    resource_id UUID,
    action VARCHAR(100) NOT NULL,
    operation_id UUID,
    policy_version BIGINT,
    outcome VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    detail TEXT,
    previous_hash VARCHAR(255),
    event_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant ON audit_event(tenant_id);
CREATE INDEX idx_audit_sequence ON audit_event(sequence);
CREATE UNIQUE INDEX idx_audit_seq_unique ON audit_event(sequence);

-- blind index for search
CREATE TABLE blind_index (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES document(id),
    index_key_version INTEGER NOT NULL DEFAULT 1,
    token_hmac VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_blind_tenant_token ON blind_index(tenant_id, token_hmac);
CREATE INDEX idx_blind_document ON blind_index(document_id);

-- key rotation
CREATE TABLE key_rotation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_kek_version INTEGER NOT NULL,
    target_kek_version INTEGER NOT NULL,
    cursor UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_documents BIGINT DEFAULT 0,
    processed_documents BIGINT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rotation_tenant ON key_rotation(tenant_id);
CREATE INDEX idx_rotation_status ON key_rotation(status);
