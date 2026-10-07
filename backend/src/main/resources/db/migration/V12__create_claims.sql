-- Recipient claim system: the company defines tasks, recipients submit proof via a public link,
-- the company reviews each claim, and approved claims become airdrop_recipients (see ClaimAdminService).

ALTER TABLE airdrops
    ADD COLUMN claims_open  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN claim_amount DECIMAL(20, 8) CHECK (claim_amount IS NULL OR claim_amount > 0);

CREATE TABLE airdrop_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id      UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    title           VARCHAR(150) NOT NULL,
    description     TEXT,
    proof_required  BOOLEAN NOT NULL DEFAULT TRUE,
    position        INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_airdrop_tasks_airdrop ON airdrop_tasks (airdrop_id, position);

CREATE TABLE airdrop_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id        UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    claimant_address  VARCHAR(255) NOT NULL,
    -- lower(trim(address)): the uniqueness key, so "0xABC" and "0xabc" can't both claim.
    address_key       VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note       TEXT,
    reviewed_by       UUID REFERENCES users(id),
    reviewed_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_claim_per_address UNIQUE (airdrop_id, address_key)
);
CREATE INDEX idx_airdrop_claims_airdrop_status ON airdrop_claims (airdrop_id, status);

CREATE TABLE claim_submissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id    UUID NOT NULL REFERENCES airdrop_claims(id) ON DELETE CASCADE,
    task_id     UUID NOT NULL REFERENCES airdrop_tasks(id),
    proof_text  TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_submission_per_task UNIQUE (claim_id, task_id)
);

-- New timeline event types for the claim flow.
ALTER TABLE airdrop_events DROP CONSTRAINT airdrop_events_event_type_check;
ALTER TABLE airdrop_events ADD CONSTRAINT airdrop_events_event_type_check CHECK (event_type IN (
    'AIRDROP_CREATED', 'VALIDATION_STARTED', 'VALIDATION_COMPLETED',
    'JOB_CREATED', 'JOB_STARTED', 'PROGRESS_UPDATED', 'RECIPIENT_FAILED',
    'AIRDROP_COMPLETED', 'AIRDROP_CANCELLED',
    'CLAIM_SUBMITTED', 'CLAIM_APPROVED', 'CLAIM_REJECTED'
));
