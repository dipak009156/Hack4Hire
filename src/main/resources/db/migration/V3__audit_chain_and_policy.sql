-- V3__audit_chain_and_policy.sql

-- 1. Audit Chain Hardening: Add chain_position and actor column
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS chain_position BIGINT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS actor VARCHAR(255);

-- Populate existing rows if any
UPDATE audit_event SET chain_position = sequence WHERE chain_position IS NULL;
UPDATE audit_event SET actor = actor_name WHERE actor IS NULL AND actor_name IS NOT NULL;

-- Per-tenant unique ordering by chain_position
CREATE INDEX IF NOT EXISTS idx_audit_tenant_chain ON audit_event(tenant_id, chain_position);
CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_tenant_chain_unique ON audit_event(tenant_id, chain_position);

-- 2. Policy Table Hardening: ensure policy_id and updated_by exist as specified in challenge brief
ALTER TABLE policy ADD COLUMN IF NOT EXISTS policy_id UUID DEFAULT gen_random_uuid();
UPDATE policy SET policy_id = id WHERE policy_id IS NULL;
ALTER TABLE policy ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'system';
CREATE INDEX IF NOT EXISTS idx_policy_policy_id ON policy(policy_id);

