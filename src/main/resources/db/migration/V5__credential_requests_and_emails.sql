-- V5__credential_requests_and_emails.sql
-- Supports Credential Requests, Redundancy Check, and In-App Email Notifications

CREATE TABLE IF NOT EXISTS credential_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    department VARCHAR(255),
    reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_credential_request_status ON credential_request(status);
CREATE INDEX IF NOT EXISTS idx_credential_request_tenant_name ON credential_request(tenant_name);

CREATE TABLE IF NOT EXISTS email_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body_html TEXT NOT NULL,
    email_type VARCHAR(50) NOT NULL,
    request_id UUID REFERENCES credential_request(id) ON DELETE SET NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_recipient ON email_notification(recipient_email);
CREATE INDEX IF NOT EXISTS idx_email_request_id ON email_notification(request_id);

