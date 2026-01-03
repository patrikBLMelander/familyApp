-- Add email column to family_member table for email-based login
ALTER TABLE family_member ADD COLUMN email VARCHAR(255);
CREATE INDEX idx_family_member_email ON family_member(email);

