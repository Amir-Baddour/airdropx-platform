CREATE TABLE airdrop_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id  UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    event_type  VARCHAR(50) NOT NULL
                CHECK (event_type IN (
                    'AIRDROP_CREATED', 'VALIDATION_STARTED', 'VALIDATION_COMPLETED',
                    'JOB_CREATED', 'JOB_STARTED', 'PROGRESS_UPDATED', 'RECIPIENT_FAILED',
                    'AIRDROP_COMPLETED', 'AIRDROP_CANCELLED'
                )),
    message     TEXT,
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_airdrop_events_airdrop_id ON airdrop_events (airdrop_id, created_at);

COMMENT ON TABLE airdrop_events IS 'Append-only activity feed for one airdrop — powers the live progress/timeline view.';
