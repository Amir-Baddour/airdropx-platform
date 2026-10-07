CREATE TABLE file_metadata (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airdrop_id     UUID NOT NULL REFERENCES airdrops(id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    file_type      VARCHAR(50),
    file_size      BIGINT,
    firebase_path  VARCHAR(500) NOT NULL,
    uploaded_by    UUID REFERENCES users(id),
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_file_metadata_airdrop_id ON file_metadata (airdrop_id);

COMMENT ON TABLE file_metadata IS
    'Schema is in place for v1; the actual Firebase upload + CSV parsing is not wired up yet — recipients are added via JSON for now. See README > Roadmap.';
