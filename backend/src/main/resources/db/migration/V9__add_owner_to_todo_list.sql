-- Add owner_id field to todo_list table to link lists to family members
ALTER TABLE todo_list ADD COLUMN owner_id VARCHAR(36);
ALTER TABLE todo_list ADD FOREIGN KEY (owner_id) REFERENCES family_member(id) ON DELETE SET NULL;

