-- Add password_hash column to family_member table for email+password authentication
-- Password will be hashed using BCrypt (60 characters)

ALTER TABLE family_member 
    ADD COLUMN password_hash VARCHAR(255) NULL;

-- Note: Existing users will have NULL password_hash and can still use device token login
-- They can set a password later through the UI
