-- 为独立问答前台补充会话终端隔离、会话偏好字段与多知识库绑定关系表。

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS terminal_type VARCHAR(32);

UPDATE chat_session
SET terminal_type = 'ADMIN'
WHERE terminal_type IS NULL;

ALTER TABLE chat_session
    ALTER COLUMN terminal_type SET DEFAULT 'ADMIN';

ALTER TABLE chat_session
    ALTER COLUMN terminal_type SET NOT NULL;

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS model_id BIGINT;

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS web_search_enabled BOOLEAN;

UPDATE chat_session
SET web_search_enabled = FALSE
WHERE web_search_enabled IS NULL;

ALTER TABLE chat_session
    ALTER COLUMN web_search_enabled SET DEFAULT FALSE;

ALTER TABLE chat_session
    ALTER COLUMN web_search_enabled SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_chat_session_model'
    ) THEN
        ALTER TABLE chat_session
            ADD CONSTRAINT fk_chat_session_model
                FOREIGN KEY (model_id) REFERENCES ai_model (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS chat_session_kb_rel (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    kb_id               BIGINT NOT NULL,
    sort_no             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, kb_id),
    CONSTRAINT fk_chat_session_kb_rel_session FOREIGN KEY (session_id) REFERENCES chat_session (id),
    CONSTRAINT fk_chat_session_kb_rel_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_kb_rel_session_id
    ON chat_session_kb_rel (session_id);

CREATE INDEX IF NOT EXISTS idx_chat_session_kb_rel_kb_id
    ON chat_session_kb_rel (kb_id);

INSERT INTO chat_session_kb_rel (session_id, kb_id, sort_no)
SELECT id, kb_id, 1
FROM chat_session
WHERE kb_id IS NOT NULL
ON CONFLICT (session_id, kb_id) DO NOTHING;

DROP INDEX IF EXISTS uk_chat_session_general_user;

CREATE INDEX IF NOT EXISTS idx_chat_session_user_terminal_scene
    ON chat_session (user_id, terminal_type, scene_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_general_user_terminal
    ON chat_session (user_id, terminal_type, scene_type)
    WHERE scene_type = 'GENERAL';
