CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    legal_name  VARCHAR(150),
    email       VARCHAR(100) NOT NULL,
    phone       VARCHAR(20),
    website     VARCHAR(255),
    description TEXT,
    country     VARCHAR(50),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING', 'DELETED')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE companies IS 'Tenant root. Every company-owned row elsewhere carries company_id and is isolated by it.';
