CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    company_id  UUID REFERENCES companies(id) ON DELETE SET NULL,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID,
    ip_address  VARCHAR(45),
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_company_created ON audit_logs (company_id, created_at);

COMMENT ON TABLE audit_logs IS 'Platform-wide, cross-tenant. Only PLATFORM_ADMIN reads this unscoped; company-scoped reads (if ever added) must filter by company_id.';
