-- 为模型定义增加“系统默认聊天模型”标记。
-- 运行时优先使用后台已选默认聊天模型；如未设置，再回退配置文件兜底默认值。

ALTER TABLE ai_model
    ADD COLUMN IF NOT EXISTS is_default_chat_model BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_default_chat_model
    ON ai_model (is_default_chat_model)
    WHERE is_default_chat_model = TRUE;
