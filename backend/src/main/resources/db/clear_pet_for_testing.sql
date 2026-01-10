-- Script to clear pet selection for testing
-- This allows you to test the egg selection flow again
-- 
-- Usage:
-- 1. To clear pet for a specific member (replace MEMBER_ID with actual UUID):
--    UPDATE the WHERE clause below with the member ID
--
-- 2. To clear ALL pets (be careful!):
--    Remove or comment out the WHERE clause

-- Clear current month's pets for a specific member
-- Replace 'YOUR_MEMBER_ID_HERE' with the actual member UUID
DELETE FROM child_pet 
WHERE member_id = 'YOUR_MEMBER_ID_HERE';

-- To clear pets for ALL members (uncomment to use):
-- DELETE FROM child_pet;

-- Optional: Also clear pet history if you want to start completely fresh
-- DELETE FROM pet_history WHERE member_id = 'YOUR_MEMBER_ID_HERE';
-- Or for all:
-- DELETE FROM pet_history;

