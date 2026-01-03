-- Add is_private field to todo_list table
ALTER TABLE todo_list ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

