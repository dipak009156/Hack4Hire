-- V2__seed_data.sql

-- 1. Tenants
INSERT INTO tenant (id, name, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'tenant-alpha', NOW()),
('22222222-2222-2222-2222-222222222222', 'tenant-beta', NOW()),
('33333333-3333-3333-3333-333333333333', 'tenant-gamma', NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. Users for tenant-alpha
INSERT INTO app_user (id, tenant_id, username, password_hash, roles, department, device_trust_level, active, created_at) VALUES
('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['ADMIN'], 'security', 'HIGH', TRUE, NOW()),
('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '11111111-1111-1111-1111-111111111111', 'user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'engineering', 'HIGH', TRUE, NOW()),
('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '11111111-1111-1111-1111-111111111111', 'user2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'finance', 'STANDARD', TRUE, NOW()),
('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa4', '11111111-1111-1111-1111-111111111111', 'auditor', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['AUDITOR'], 'audit', 'HIGH', TRUE, NOW())
ON CONFLICT (tenant_id, username) DO NOTHING;

-- 3. Users for tenant-beta
INSERT INTO app_user (id, tenant_id, username, password_hash, roles, department, device_trust_level, active, created_at) VALUES
('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '22222222-2222-2222-2222-222222222222', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['ADMIN'], 'security', 'HIGH', TRUE, NOW()),
('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '22222222-2222-2222-2222-222222222222', 'user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'engineering', 'HIGH', TRUE, NOW()),
('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbb3', '22222222-2222-2222-2222-222222222222', 'user2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'finance', 'STANDARD', TRUE, NOW()),
('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbb4', '22222222-2222-2222-2222-222222222222', 'auditor', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['AUDITOR'], 'audit', 'HIGH', TRUE, NOW())
ON CONFLICT (tenant_id, username) DO NOTHING;

-- 4. Users for tenant-gamma
INSERT INTO app_user (id, tenant_id, username, password_hash, roles, department, device_trust_level, active, created_at) VALUES
('33333333-cccc-cccc-cccc-ccccccccccc1', '33333333-3333-3333-3333-333333333333', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['ADMIN'], 'security', 'HIGH', TRUE, NOW()),
('33333333-cccc-cccc-cccc-ccccccccccc2', '33333333-3333-3333-3333-333333333333', 'user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'engineering', 'HIGH', TRUE, NOW()),
('33333333-cccc-cccc-cccc-ccccccccccc3', '33333333-3333-3333-3333-333333333333', 'user2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['USER'], 'finance', 'STANDARD', TRUE, NOW()),
('33333333-cccc-cccc-cccc-ccccccccccc4', '33333333-3333-3333-3333-333333333333', 'auditor', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W', ARRAY['AUDITOR'], 'audit', 'HIGH', TRUE, NOW())
ON CONFLICT (tenant_id, username) DO NOTHING;

-- 5. Default policies per tenant
INSERT INTO policy (id, tenant_id, name, expression, version, created_at, updated_at) VALUES
(gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'default-internal-access', 'classification == "INTERNAL" || roles.contains("ADMIN")', 1, NOW(), NOW()),
(gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'default-internal-access', 'classification == "INTERNAL" || roles.contains("ADMIN")', 1, NOW(), NOW()),
(gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 'default-internal-access', 'classification == "INTERNAL" || roles.contains("ADMIN")', 1, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;
