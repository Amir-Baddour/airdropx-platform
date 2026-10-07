CREATE TABLE airdrop_recipients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id          UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    recipient_address   VARCHAR(255) NOT NULL,
    amount              DECIMAL(20, 8) NOT NULL CHECK (amount > 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    external_reference  VARCHAR(255),
    error_message       TEXT,
    processed_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_airdrop_recipients_airdrop_status ON airdrop_recipients (airdrop_id, status);
