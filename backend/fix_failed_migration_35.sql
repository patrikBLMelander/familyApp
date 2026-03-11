-- Script to fix failed migration V35
-- Run this manually against the database to clean up the failed migration
-- Then restart the backend to allow V35 to run again

-- Step 1: Remove any partially created wallet tables
DROP TABLE IF EXISTS wallet_notification;
DROP TABLE IF EXISTS wallet_transaction_savings_goal;
DROP TABLE IF EXISTS savings_goal;
DROP TABLE IF EXISTS wallet_transaction;
DROP TABLE IF EXISTS child_wallet;
DROP TABLE IF EXISTS expense_category;

-- Step 2: Remove the failed migration record from flyway_schema_history
DELETE FROM flyway_schema_history 
WHERE version = '35' 
AND success = 0;
