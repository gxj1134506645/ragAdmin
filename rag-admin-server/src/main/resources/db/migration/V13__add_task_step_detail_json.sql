ALTER TABLE job_task_step_record
    ADD COLUMN IF NOT EXISTS detail_json JSONB;
