CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key              VARCHAR(255) NOT NULL,
    endpoint         VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(255) NOT NULL,
    response_status  INTEGER,
    response_body    JSONB,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    expires_at       TIMESTAMP NOT NULL
);

-- The actual guarantee: one user cannot reuse the same client-supplied key for two different requests.
CREATE UNIQUE INDEX idx_idempotency_user_key ON idempotency_keys (user_id, key);

COMMENT ON TABLE idempotency_keys IS 'Protects mutating endpoints (airdrop launch) from duplicate execution on client retry. See IdempotencyService.';
