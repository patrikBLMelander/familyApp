-- Repair failed migration V26
-- Mark the failed migration as successful or remove it
-- This allows the application to start even if V26 failed previously

-- Option 1: Mark as successful (if you want to keep the record)
-- UPDATE flyway_schema_history SET success = 1 WHERE version = '26' AND success = 0;

-- Option 2: Remove the failed record (allows V26 to run again)
DELETE FROM flyway_schema_history 
WHERE version = '26' 
AND success = 0;
