ALTER TABLE job_task_step_record
    DROP CONSTRAINT IF EXISTS fk_job_task_step_record_task;

ALTER TABLE job_task_step_record
    ADD CONSTRAINT fk_job_task_step_record_parse_task
        FOREIGN KEY (task_id) REFERENCES kb_document_parse_task (id);

ALTER TABLE job_retry_record
    DROP CONSTRAINT IF EXISTS fk_job_retry_record_task;

ALTER TABLE job_retry_record
    ADD CONSTRAINT fk_job_retry_record_parse_task
        FOREIGN KEY (task_id) REFERENCES kb_document_parse_task (id);
