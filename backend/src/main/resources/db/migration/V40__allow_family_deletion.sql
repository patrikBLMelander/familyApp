-- Deleting a family (or a single member) is blocked today.
--
-- Almost every foreign key in the schema already cascades, so removing a family row
-- would clear its members, chores, pets, wallets, calendar and lists in one step.
-- Three constraints in the wallet tables do not cascade, and MySQL defaults them to
-- RESTRICT, so any family that has ever paid out allowance cannot be deleted:
--
--   wallet_transaction.created_by_member_id -> family_member(id)
--   wallet_transaction.deleted_by_member_id -> family_member(id)
--   savings_goal.purchase_transaction_id    -> wallet_transaction(id)
--
-- The same three block removing an individual parent, which the Android app now
-- offers. A parent who has given allowance could not be removed.
--
-- SET NULL rather than CASCADE is deliberate for the two audit columns: the
-- transaction is a financial record for the child and should survive whoever
-- entered it. Losing the name is acceptable; losing the transaction is not.
--
-- The original constraints were created unnamed, so MySQL generated names like
-- wallet_transaction_ibfk_3. Those are looked up rather than guessed, and the
-- replacements are named so later migrations can address them directly.

-- 1. wallet_transaction.created_by_member_id
SET @fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_transaction'
      AND COLUMN_NAME = 'created_by_member_id'
      AND REFERENCED_TABLE_NAME = 'family_member'
    LIMIT 1
);
SET @sql := IF(@fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE wallet_transaction DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE wallet_transaction
    ADD CONSTRAINT fk_wallet_tx_created_by
    FOREIGN KEY (created_by_member_id) REFERENCES family_member(id) ON DELETE SET NULL;

-- 2. wallet_transaction.deleted_by_member_id
SET @fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_transaction'
      AND COLUMN_NAME = 'deleted_by_member_id'
      AND REFERENCED_TABLE_NAME = 'family_member'
    LIMIT 1
);
SET @sql := IF(@fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE wallet_transaction DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE wallet_transaction
    ADD CONSTRAINT fk_wallet_tx_deleted_by
    FOREIGN KEY (deleted_by_member_id) REFERENCES family_member(id) ON DELETE SET NULL;

-- 3. savings_goal.purchase_transaction_id
--    The goal is the child's own record and outlives the transaction that funded it.
SET @fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'savings_goal'
      AND COLUMN_NAME = 'purchase_transaction_id'
      AND REFERENCED_TABLE_NAME = 'wallet_transaction'
    LIMIT 1
);
SET @sql := IF(@fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE savings_goal DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE savings_goal
    ADD CONSTRAINT fk_savings_goal_purchase_tx
    FOREIGN KEY (purchase_transaction_id) REFERENCES wallet_transaction(id) ON DELETE SET NULL;
