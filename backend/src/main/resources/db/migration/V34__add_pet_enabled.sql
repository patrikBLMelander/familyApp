-- Add pet enabled setting to family members
-- Allows adults (PARENT) to opt-in to having pets

ALTER TABLE family_member ADD COLUMN pet_enabled BOOLEAN DEFAULT FALSE;
