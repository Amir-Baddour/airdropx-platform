CREATE TABLE airdrops (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    created_by       UUID NOT NULL REFERENCES users(id),
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    asset_type       VARCHAR(50) NOT NULL,
    total_amount     DECIMAL(20, 8) NOT NULL DEFAULT 0,
    recipient_count  INTEGER NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN (
                        'DRAFT', 'VALIDATING', 'READY', 'SCHEDULED', 'QUEUED',
                        'RUNNING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'
                     )),
    scheduled_at     TIMESTAMP,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_airdrops_company_status ON airdrops (company_id, status);
CREATE INDEX idx_airdrops_created_by ON airdrops (created_by);

COMMENT ON COLUMN airdrops.asset_type IS
    'Free-text label only (e.g. "USDT", "Loyalty Points") — this project does not move real assets. See README > Mocked Distribution.';
