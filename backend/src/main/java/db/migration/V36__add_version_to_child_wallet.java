package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Add version column to child_wallet for optimistic locking
 * This prevents race conditions when multiple requests update the balance simultaneously.
 */
public class V36__add_version_to_child_wallet extends BaseJavaMigration {
    
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            System.out.println("V36: Adding version column to child_wallet for optimistic locking...");
            
            // Check if column already exists (idempotent migration)
            var checkColumn = statement.executeQuery("""
                SELECT COUNT(*) as count
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'child_wallet'
                AND COLUMN_NAME = 'version'
            """);
            
            checkColumn.next();
            int columnExists = checkColumn.getInt("count");
            
            if (columnExists == 0) {
                // Add version column with default value 0 for existing rows
                statement.execute("""
                    ALTER TABLE child_wallet
                    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
                """);
                
                System.out.println("V36: Version column added successfully");
            } else {
                System.out.println("V36: Version column already exists, skipping");
            }
        }
    }
}
