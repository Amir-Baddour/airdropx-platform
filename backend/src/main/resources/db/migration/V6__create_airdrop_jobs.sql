CREATE TABLE airdrop_jobs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id     UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
                   CHECK (status IN ('QUEUED', 'STARTING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    worker_id      VARCHAR(50),
    attempts       INTEGER NOT NULL DEFAULT 0,
    progress       INTEGER NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    started_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    error_message  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_airdrop_jobs_airdrop_status ON airdrop_jobs (airdrop_id, status);
-- Used on worker startup to find jobs orphaned by a crash/restart (see AirdropWorker.recoverStuckJobs()).
CREATE INDEX idx_airdrop_jobs_status_running ON airdrop_jobs (status) WHERE status = 'RUNNING';
