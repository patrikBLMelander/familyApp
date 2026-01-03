-- Fix unique constraint on daily_task_completion to allow multiple members to complete the same task
-- Remove old constraint that only included task_id and completed_date
ALTER TABLE daily_task_completion DROP INDEX unique_task_date;

-- Delete any existing completions without member_id (legacy data)
DELETE FROM daily_task_completion WHERE member_id IS NULL;

-- Drop the existing foreign key constraint that has ON DELETE SET NULL
-- The constraint name is typically daily_task_completion_ibfk_2 based on the error message
ALTER TABLE daily_task_completion DROP FOREIGN KEY daily_task_completion_ibfk_2;

-- Now make member_id NOT NULL
ALTER TABLE daily_task_completion 
    MODIFY COLUMN member_id VARCHAR(36) NOT NULL;

-- Add new foreign key constraint with ON DELETE CASCADE
ALTER TABLE daily_task_completion 
    ADD CONSTRAINT fk_daily_task_completion_member 
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE;

-- Add new unique constraint that includes member_id
-- This allows multiple members to have separate completions for the same task on the same date
ALTER TABLE daily_task_completion 
    ADD UNIQUE KEY unique_task_date_member (task_id, completed_date, member_id);

