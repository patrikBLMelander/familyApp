-- Add unique constraint on email column to ensure one email = one account
-- This prevents duplicate accounts with the same email

-- First, remove any duplicate emails (keep the first one)
-- Note: This is a safety measure. In practice, emails should already be unique.

-- Add unique index on email (NULL values are allowed and don't conflict)
CREATE UNIQUE INDEX idx_family_member_email_unique ON family_member(email);
