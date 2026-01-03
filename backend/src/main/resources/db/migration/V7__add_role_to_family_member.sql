-- Add role field to family_member table
-- CHILD = barn, PARENT = förälder
ALTER TABLE family_member ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CHILD';

