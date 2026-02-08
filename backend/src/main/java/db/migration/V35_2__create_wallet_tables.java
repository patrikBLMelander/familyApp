package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Create wallet tables
 * This migration creates all wallet-related tables with correct dependency order.
 * V35_1 cleaned up the failed V35 migration, and this migration actually creates the tables.
 */
public class V35_2__create_wallet_tables extends BaseJavaMigration {
    
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            System.out.println("V35_2: Creating wallet tables...");
            
            // Expense categories (no dependencies)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS expense_category (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(50) NOT NULL UNIQUE,
                    emoji VARCHAR(10),
                    is_default BOOLEAN DEFAULT FALSE,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
            """);
            
            // Insert default categories (only if they don't exist)
            statement.execute("""
                INSERT IGNORE INTO expense_category (id, name, emoji, is_default, created_at) 
                VALUES (UUID(), 'Godis', '🍬', TRUE, NOW())
            """);
            statement.execute("""
                INSERT IGNORE INTO expense_category (id, name, emoji, is_default, created_at) 
                VALUES (UUID(), 'Leksaker', '🧸', TRUE, NOW())
            """);
            statement.execute("""
                INSERT IGNORE INTO expense_category (id, name, emoji, is_default, created_at) 
                VALUES (UUID(), 'Kläder', '👕', TRUE, NOW())
            """);
            
            // Child wallet (depends on family_member)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS child_wallet (
                    id VARCHAR(36) PRIMARY KEY,
                    member_id VARCHAR(36) NOT NULL UNIQUE,
                    balance INTEGER NOT NULL DEFAULT 0,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
                )
            """);
            
            // Wallet transactions (depends on child_wallet and expense_category)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS wallet_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    wallet_id VARCHAR(36) NOT NULL,
                    amount INTEGER NOT NULL,
                    transaction_type VARCHAR(20) NOT NULL,
                    description VARCHAR(255),
                    category_id VARCHAR(36),
                    created_by_member_id VARCHAR(36),
                    is_deleted BOOLEAN DEFAULT FALSE,
                    deleted_at DATETIME(6),
                    deleted_by_member_id VARCHAR(36),
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    FOREIGN KEY (wallet_id) REFERENCES child_wallet(id) ON DELETE CASCADE,
                    FOREIGN KEY (category_id) REFERENCES expense_category(id),
                    FOREIGN KEY (created_by_member_id) REFERENCES family_member(id),
                    FOREIGN KEY (deleted_by_member_id) REFERENCES family_member(id)
                )
            """);
            
            // Savings goals (depends on wallet_transaction, so must be created after)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS savings_goal (
                    id VARCHAR(36) PRIMARY KEY,
                    member_id VARCHAR(36) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    target_amount INTEGER NOT NULL,
                    current_amount INTEGER NOT NULL DEFAULT 0,
                    emoji VARCHAR(10),
                    is_active BOOLEAN DEFAULT TRUE,
                    is_completed BOOLEAN DEFAULT FALSE,
                    is_purchased BOOLEAN DEFAULT FALSE,
                    completed_at DATETIME(6),
                    purchased_at DATETIME(6),
                    purchase_transaction_id VARCHAR(36),
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
                    FOREIGN KEY (purchase_transaction_id) REFERENCES wallet_transaction(id)
                )
            """);
            
            // Junction table (depends on wallet_transaction and savings_goal)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS wallet_transaction_savings_goal (
                    transaction_id VARCHAR(36) NOT NULL,
                    savings_goal_id VARCHAR(36) NOT NULL,
                    amount INTEGER NOT NULL,
                    PRIMARY KEY (transaction_id, savings_goal_id),
                    FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id) ON DELETE CASCADE,
                    FOREIGN KEY (savings_goal_id) REFERENCES savings_goal(id) ON DELETE CASCADE
                )
            """);
            
            // Wallet notifications (depends on wallet_transaction)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS wallet_notification (
                    id VARCHAR(36) PRIMARY KEY,
                    member_id VARCHAR(36) NOT NULL,
                    transaction_id VARCHAR(36) NOT NULL,
                    amount INTEGER NOT NULL,
                    description VARCHAR(255),
                    shown_at DATETIME(6),
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
                    FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id) ON DELETE CASCADE
                )
            """);
            
            // Create indexes (IF NOT EXISTS doesn't work for indexes in MySQL, so we check first)
            createIndexIfNotExists(statement, "idx_child_wallet_member_id", "child_wallet(member_id)");
            createIndexIfNotExists(statement, "idx_savings_goal_member_id", "savings_goal(member_id)");
            createIndexIfNotExists(statement, "idx_savings_goal_active", "savings_goal(member_id, is_active, is_completed)");
            createIndexIfNotExists(statement, "idx_wallet_transaction_wallet_id", "wallet_transaction(wallet_id)");
            createIndexIfNotExists(statement, "idx_wallet_transaction_created_at", "wallet_transaction(wallet_id, created_at DESC)");
            createIndexIfNotExists(statement, "idx_wallet_transaction_category", "wallet_transaction(category_id)");
            createIndexIfNotExists(statement, "idx_wallet_transaction_savings_goal_transaction", "wallet_transaction_savings_goal(transaction_id)");
            createIndexIfNotExists(statement, "idx_wallet_transaction_savings_goal_goal", "wallet_transaction_savings_goal(savings_goal_id)");
            createIndexIfNotExists(statement, "idx_wallet_notification_member_unshown", "wallet_notification(member_id, shown_at)");
            
            System.out.println("V35_2: Successfully created all wallet tables and indexes");
        }
    }
    
    private void createIndexIfNotExists(Statement statement, String indexName, String tableAndColumns) throws Exception {
        // Check if index exists by querying information_schema
        var checkSql = String.format(
            "SELECT COUNT(*) FROM information_schema.statistics " +
            "WHERE table_schema = DATABASE() " +
            "AND index_name = '%s'",
            indexName
        );
        
        try (var rs = statement.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                statement.execute("CREATE INDEX " + indexName + " ON " + tableAndColumns);
            }
        }
    }
}
