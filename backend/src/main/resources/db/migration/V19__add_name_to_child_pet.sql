-- Add name column to child_pet table
-- Allows children to name their pets

ALTER TABLE child_pet
    ADD COLUMN name VARCHAR(100) NULL; -- NULL for backward compatibility, can be set later

