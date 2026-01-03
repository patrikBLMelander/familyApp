-- Add member_id to daily_task_completion to track which member completed the task
ALTER TABLE daily_task_completion ADD COLUMN member_id VARCHAR(36);
ALTER TABLE daily_task_completion ADD FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE SET NULL;

