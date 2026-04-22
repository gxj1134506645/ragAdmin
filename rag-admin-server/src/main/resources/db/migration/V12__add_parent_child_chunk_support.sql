ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS parent_chunk_id BIGINT,
    ADD COLUMN IF NOT EXISTS chunk_strategy VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_parent_chunk_id ON kb_chunk (parent_chunk_id);
