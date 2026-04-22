ALTER TABLE kb_knowledge_base
    ADD COLUMN IF NOT EXISTS retrieval_query_rewriting_mode VARCHAR(32) NOT NULL DEFAULT 'NONE';

COMMENT ON COLUMN kb_knowledge_base.retrieval_query_rewriting_mode IS '查询改写模式: NONE, MULTI_QUERY, HYDE, MULTI_QUERY_AND_HYDE';
