-- RAG 知识库管理系统 PostgreSQL 建表草案 V1
-- 说明：
-- 1. 本草案面向首期内部平台
-- 2. 默认数据库为 PostgreSQL 16
-- 3. 数据库结构变更统一通过 Flyway 管理
-- 4. 向量本体不落 PostgreSQL，首期仅保存 Milvus 引用信息
-- 5. embedding 维度暂按 1024 设计，如后续切换模型需同步调整

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    email           VARCHAR(128),
    mobile          VARCHAR(32),
    status          VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username),
    UNIQUE (mobile)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGSERIAL PRIMARY KEY,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(100) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id                  BIGSERIAL PRIMARY KEY,
    parent_id           BIGINT NOT NULL DEFAULT 0,
    menu_type           VARCHAR(16) NOT NULL,
    menu_code           VARCHAR(100) NOT NULL,
    menu_name           VARCHAR(100) NOT NULL,
    path                VARCHAR(255),
    component           VARCHAR(255),
    permission_code     VARCHAR(100),
    sort_no             INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (menu_code)
);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    operator_user_id    BIGINT,
    operator_username   VARCHAR(64),
    action_type         VARCHAR(64) NOT NULL,
    biz_type            VARCHAR(64) NOT NULL,
    biz_id              BIGINT,
    request_method      VARCHAR(16),
    request_path        VARCHAR(255),
    request_ip          VARCHAR(64),
    request_payload     TEXT,
    response_code       VARCHAR(64),
    success             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_audit_log_operator_user_id ON sys_audit_log (operator_user_id);
CREATE INDEX IF NOT EXISTS idx_sys_audit_log_biz_type_biz_id ON sys_audit_log (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_sys_audit_log_created_at ON sys_audit_log (created_at);

CREATE TABLE IF NOT EXISTS ai_provider (
    id                  BIGSERIAL PRIMARY KEY,
    provider_code       VARCHAR(64) NOT NULL,
    provider_name       VARCHAR(100) NOT NULL,
    base_url            VARCHAR(255),
    api_key_secret_ref  VARCHAR(255),
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_code)
);

CREATE TABLE IF NOT EXISTS ai_model (
    id                  BIGSERIAL PRIMARY KEY,
    provider_id         BIGINT NOT NULL,
    model_code          VARCHAR(100) NOT NULL,
    model_name          VARCHAR(100) NOT NULL,
    model_type          VARCHAR(32) NOT NULL,
    max_tokens          INTEGER,
    temperature_default NUMERIC(6, 3),
    is_default_chat_model BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_id, model_code),
    CONSTRAINT fk_ai_model_provider FOREIGN KEY (provider_id) REFERENCES ai_provider (id)
);

CREATE INDEX IF NOT EXISTS idx_ai_model_provider_id ON ai_model (provider_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_default_chat_model ON ai_model (is_default_chat_model) WHERE is_default_chat_model = TRUE;

CREATE TABLE IF NOT EXISTS ai_model_capability (
    id                  BIGSERIAL PRIMARY KEY,
    model_id            BIGINT NOT NULL,
    capability_type     VARCHAR(64) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (model_id, capability_type),
    CONSTRAINT fk_ai_model_capability_model FOREIGN KEY (model_id) REFERENCES ai_model (id)
);

CREATE TABLE IF NOT EXISTS ai_model_route (
    id                  BIGSERIAL PRIMARY KEY,
    route_scope         VARCHAR(32) NOT NULL,
    scope_ref_id        BIGINT,
    capability_type     VARCHAR(64) NOT NULL,
    model_id            BIGINT NOT NULL,
    priority_no         INTEGER NOT NULL DEFAULT 100,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_model_route_model FOREIGN KEY (model_id) REFERENCES ai_model (id)
);

CREATE INDEX IF NOT EXISTS idx_ai_model_route_scope ON ai_model_route (route_scope, scope_ref_id);

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id                  BIGSERIAL PRIMARY KEY,
    template_code       VARCHAR(100) NOT NULL,
    template_name       VARCHAR(100) NOT NULL,
    capability_type     VARCHAR(64) NOT NULL,
    prompt_content      TEXT NOT NULL,
    version_no          INTEGER NOT NULL DEFAULT 1,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (template_code, version_no)
);

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id                  BIGSERIAL PRIMARY KEY,
    kb_code             VARCHAR(100) NOT NULL,
    kb_name             VARCHAR(100) NOT NULL,
    description         TEXT,
    embedding_model_id  BIGINT,
    chat_model_id       BIGINT,
    retrieve_top_k      INTEGER NOT NULL DEFAULT 5,
    rerank_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (kb_code),
    CONSTRAINT fk_kb_embedding_model FOREIGN KEY (embedding_model_id) REFERENCES ai_model (id),
    CONSTRAINT fk_kb_chat_model FOREIGN KEY (chat_model_id) REFERENCES ai_model (id)
);

CREATE TABLE IF NOT EXISTS kb_document (
    id                  BIGSERIAL PRIMARY KEY,
    kb_id               BIGINT NOT NULL,
    doc_name            VARCHAR(255) NOT NULL,
    doc_type            VARCHAR(32) NOT NULL,
    storage_bucket      VARCHAR(100) NOT NULL,
    storage_object_key  VARCHAR(500) NOT NULL,
    current_version     INTEGER NOT NULL DEFAULT 1,
    parse_status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    file_size           BIGINT,
    content_hash        VARCHAR(128),
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_document_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id)
);

CREATE INDEX IF NOT EXISTS idx_kb_document_kb_id ON kb_document (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_parse_status ON kb_document (parse_status);

CREATE TABLE IF NOT EXISTS kb_document_version (
    id                  BIGSERIAL PRIMARY KEY,
    document_id         BIGINT NOT NULL,
    version_no          INTEGER NOT NULL,
    storage_bucket      VARCHAR(100) NOT NULL,
    storage_object_key  VARCHAR(500) NOT NULL,
    content_hash        VARCHAR(128),
    parse_status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    parse_started_at    TIMESTAMP,
    parse_finished_at   TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, version_no),
    CONSTRAINT fk_kb_document_version_document FOREIGN KEY (document_id) REFERENCES kb_document (id)
);

CREATE INDEX IF NOT EXISTS idx_kb_document_version_document_id ON kb_document_version (document_id);

CREATE TABLE IF NOT EXISTS kb_document_parse_task (
    id                  BIGSERIAL PRIMARY KEY,
    kb_id               BIGINT NOT NULL,
    document_id         BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    task_status         VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    error_message       TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_parse_task_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id),
    CONSTRAINT fk_kb_parse_task_document FOREIGN KEY (document_id) REFERENCES kb_document (id),
    CONSTRAINT fk_kb_parse_task_document_version FOREIGN KEY (document_version_id) REFERENCES kb_document_version (id)
);

CREATE INDEX IF NOT EXISTS idx_kb_document_parse_task_document_id ON kb_document_parse_task (document_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_parse_task_status ON kb_document_parse_task (task_status);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id                  BIGSERIAL PRIMARY KEY,
    kb_id               BIGINT NOT NULL,
    document_id         BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    chunk_no            INTEGER NOT NULL,
    chunk_text          TEXT NOT NULL,
    token_count         INTEGER,
    char_count          INTEGER,
    metadata_json       JSONB,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_version_id, chunk_no),
    CONSTRAINT fk_kb_chunk_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id),
    CONSTRAINT fk_kb_chunk_document FOREIGN KEY (document_id) REFERENCES kb_document (id),
    CONSTRAINT fk_kb_chunk_document_version FOREIGN KEY (document_version_id) REFERENCES kb_document_version (id)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_document_id ON kb_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_document_version_id ON kb_chunk (document_version_id);

CREATE TABLE IF NOT EXISTS kb_chunk_vector_ref (
    id                  BIGSERIAL PRIMARY KEY,
    kb_id               BIGINT NOT NULL,
    chunk_id            BIGINT NOT NULL,
    embedding_model_id  BIGINT NOT NULL,
    collection_name     VARCHAR(128) NOT NULL,
    partition_name      VARCHAR(128),
    vector_id           VARCHAR(128) NOT NULL,
    embedding_dim       INTEGER NOT NULL DEFAULT 1024,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (chunk_id, embedding_model_id),
    UNIQUE (vector_id),
    CONSTRAINT fk_kb_chunk_vector_ref_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id),
    CONSTRAINT fk_kb_chunk_vector_ref_chunk FOREIGN KEY (chunk_id) REFERENCES kb_chunk (id),
    CONSTRAINT fk_kb_chunk_vector_ref_model FOREIGN KEY (embedding_model_id) REFERENCES ai_model (id)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector_ref_kb_id ON kb_chunk_vector_ref (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector_ref_collection_name ON kb_chunk_vector_ref (collection_name);

CREATE TABLE IF NOT EXISTS chat_session (
    id                  BIGSERIAL PRIMARY KEY,
    kb_id               BIGINT,
    user_id             BIGINT NOT NULL,
    scene_type          VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_BASE',
    terminal_type       VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    session_name        VARCHAR(200) NOT NULL,
    model_id            BIGINT,
    web_search_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_kb FOREIGN KEY (kb_id) REFERENCES kb_knowledge_base (id),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_chat_session_model FOREIGN KEY (model_id) REFERENCES ai_model (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_kb_user ON chat_session (kb_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_terminal_scene ON chat_session (user_id, terminal_type, scene_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_admin_general_user_terminal
    ON chat_session (user_id, terminal_type, scene_type)
    WHERE scene_type = 'GENERAL' AND terminal_type = 'ADMIN';

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

CREATE INDEX IF NOT EXISTS idx_chat_session_kb_rel_session_id ON chat_session_kb_rel (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_kb_rel_kb_id ON chat_session_kb_rel (kb_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    message_type        VARCHAR(16) NOT NULL,
    question_text       TEXT,
    answer_text         TEXT,
    model_id            BIGINT,
    answer_confidence   VARCHAR(16),
    has_knowledge_base_evidence BOOLEAN,
    need_follow_up      BOOLEAN,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    latency_ms          INTEGER,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id),
    CONSTRAINT fk_chat_message_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_chat_message_model FOREIGN KEY (model_id) REFERENCES ai_model (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_created_at ON chat_message (created_at);

CREATE TABLE IF NOT EXISTS chat_answer_reference (
    id                  BIGSERIAL PRIMARY KEY,
    message_id          BIGINT NOT NULL,
    chunk_id            BIGINT NOT NULL,
    score               NUMERIC(8, 6),
    rank_no             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_answer_reference_message FOREIGN KEY (message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_answer_reference_chunk FOREIGN KEY (chunk_id) REFERENCES kb_chunk (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_answer_reference_message_id ON chat_answer_reference (message_id);

CREATE TABLE IF NOT EXISTS chat_feedback (
    id                  BIGSERIAL PRIMARY KEY,
    message_id          BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    feedback_type       VARCHAR(16) NOT NULL,
    comment_text        VARCHAR(500),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, user_id),
    CONSTRAINT fk_chat_feedback_message FOREIGN KEY (message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_feedback_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(128) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id_timestamp
    ON spring_ai_chat_memory (conversation_id, "timestamp");

CREATE TABLE IF NOT EXISTS chat_session_memory_summary (
    id                          BIGSERIAL PRIMARY KEY,
    session_id                  BIGINT NOT NULL,
    conversation_id             VARCHAR(128) NOT NULL,
    summary_text                TEXT NOT NULL,
    summary_version             INTEGER NOT NULL DEFAULT 1,
    compressed_message_count    INTEGER NOT NULL DEFAULT 0,
    compressed_until_message_id BIGINT,
    last_source_message_id      BIGINT,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_memory_summary_session FOREIGN KEY (session_id) REFERENCES chat_session (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_memory_summary_session_id
    ON chat_session_memory_summary (session_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_memory_summary_conversation_id
    ON chat_session_memory_summary (conversation_id);

CREATE TABLE IF NOT EXISTS job_task_record (
    id                  BIGSERIAL PRIMARY KEY,
    task_type           VARCHAR(64) NOT NULL,
    task_status         VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    biz_type            VARCHAR(64) NOT NULL,
    biz_id              BIGINT NOT NULL,
    request_payload     JSONB,
    result_payload      JSONB,
    error_message       TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_task_record_biz ON job_task_record (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_job_task_record_status ON job_task_record (task_status);

CREATE TABLE IF NOT EXISTS job_task_step_record (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL,
    step_code           VARCHAR(64) NOT NULL,
    step_name           VARCHAR(100) NOT NULL,
    step_status         VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    error_message       TEXT,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_task_step_record_task FOREIGN KEY (task_id) REFERENCES kb_document_parse_task (id)
);

CREATE INDEX IF NOT EXISTS idx_job_task_step_record_task_id ON job_task_step_record (task_id);

CREATE TABLE IF NOT EXISTS job_retry_record (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL,
    retry_no            INTEGER NOT NULL,
    retry_reason        VARCHAR(255),
    retry_result        VARCHAR(16),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_retry_record_task FOREIGN KEY (task_id) REFERENCES kb_document_parse_task (id)
);

CREATE INDEX IF NOT EXISTS idx_job_retry_record_task_id ON job_retry_record (task_id);

INSERT INTO sys_role (role_code, role_name)
VALUES
    ('ADMIN', '系统管理员'),
    ('KB_ADMIN', '知识库管理员'),
    ('USER', '普通用户'),
    ('APP_USER', '问答前台用户'),
    ('AUDITOR', '审计用户')
ON CONFLICT (role_code) DO NOTHING;
