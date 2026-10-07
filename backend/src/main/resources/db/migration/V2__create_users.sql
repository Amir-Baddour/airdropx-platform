CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID REFERENCES companies(id) ON DELETE CASCADE,
    email          VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(50) NOT NULL,
    last_name      VARCHAR(50) NOT NULL,
    role           VARCHAR(20) NOT NULL
                   CHECK (role IN ('PLATFORM_ADMIN', 'COMPANY_OWNER', 'COMPANY_USER')),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING', 'DELETED')),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),

    -- PLATFORM_ADMIN users are not scoped to a company; everyone else must be.
    CONSTRAINT chk_company_scope CHECK (
        (role = 'PLATFORM_ADMIN' AND company_id IS NULL) OR
        (role <> 'PLATFORM_ADMIN' AND company_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_company_id ON users (company_id);
