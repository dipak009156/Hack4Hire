-- V4__sharing_rotation_search.sql
-- Adds indexes/constraints for sharing, blind search, and key rotation
-- Note: access_grant, blind_index, key_rotation tables already exist from V1

-- Ensure access_grant row_version starts at 0 for OCC
ALTER TABLE access_grant ALTER COLUMN row_version SET DEFAULT 0;

-- Composite index for fast grant lookups per document+grantee
CREATE INDEX IF NOT EXISTS idx_grant_document_grantee ON access_grant(document_id, grantee_id);
-- Partial index: only active (non-revoked) grants
CREATE INDEX IF NOT EXISTS idx_grant_active ON access_grant(document_id, grantee_id)
    WHERE revoked_at IS NULL;

-- Blind index: composite lookup
CREATE INDEX IF NOT EXISTS idx_blind_tenant_doc ON blind_index(tenant_id, document_id);

-- Key rotation: composite index for active rotations per tenant
CREATE INDEX IF NOT EXISTS idx_rotation_tenant_status ON key_rotation(tenant_id, status);

