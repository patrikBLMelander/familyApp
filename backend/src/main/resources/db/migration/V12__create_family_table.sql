-- Create family table for multi-tenancy
CREATE TABLE family (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- Add family_id to family_member table
ALTER TABLE family_member ADD COLUMN family_id VARCHAR(36);
ALTER TABLE family_member ADD FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE;

-- Add family_id to todo_list table
ALTER TABLE todo_list ADD COLUMN family_id VARCHAR(36);
ALTER TABLE todo_list ADD FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE;

-- Add family_id to daily_task table
ALTER TABLE daily_task ADD COLUMN family_id VARCHAR(36);
ALTER TABLE daily_task ADD FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE;

-- Create indexes for better performance
CREATE INDEX idx_family_member_family_id ON family_member(family_id);
CREATE INDEX idx_todo_list_family_id ON todo_list(family_id);
CREATE INDEX idx_daily_task_family_id ON daily_task(family_id);

