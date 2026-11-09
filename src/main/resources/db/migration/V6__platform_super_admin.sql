-- V6__platform_super_admin.sql
-- Introduces the platform-level tenant and SUPER_ADMIN user.
-- SUPER_ADMIN is the only role authorized to review credential requests.
-- The platform tenant is NOT an isolated tenant for documents; it is the
-- governance layer that manages onboarding of all isolated tenants.

INSERT INTO tenant (id, name, created_at) VALUES
  ('00000000-0000-0000-0000-000000000001', 'platform', NOW())
ON CONFLICT (id) DO NOTHING;

-- password_hash below is bcrypt of "password123" (same as seed users)
INSERT INTO app_user (id, tenant_id, username, password_hash, roles, department, device_trust_level, active, created_at) VALUES
  ('00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000001',
   'superadmin',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnTv23WLX5W',
   ARRAY['SUPER_ADMIN'],
   'platform',
   'HIGH',
   TRUE,
   NOW())
ON CONFLICT (tenant_id, username) DO NOTHING;

