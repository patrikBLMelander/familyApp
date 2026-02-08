package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Repair script for failed migration V35 (LOCAL DEVELOPMENT ONLY)
 * 
 * This migration is ONLY needed if V35 failed during local development.
 * In production, V35 should run successfully on first attempt.
 * 
 * This migration:
 * 1. Checks if V35 failed and needs repair
 * 2. If repair is needed: drops partially created tables and removes failed migration record
 * 3. If repair is NOT needed: does nothing (idempotent)
 * 
 * NOTE: This migration does NOT create tables - that's handled by V35_2.
 * This is purely a cleanup/repair migration for local development scenarios.
 */
public class V35_1__repair_failed_migration_35 extends BaseJavaMigration {
    
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            System.out.println("V35_1: Checking if repair is needed...");
            
            // Check if there's a failed V35 migration
            var checkSql = "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '35' AND success = 0";
            boolean needsRepair = false;
            
            try (var rs = statement.executeQuery(checkSql)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    needsRepair = true;
                }
            }
            
            if (!needsRepair) {
                System.out.println("V35_1: No repair needed - V35 either succeeded or was never attempted. Skipping.");
                return;
            }
            
            System.out.println("V35_1: Repair needed - cleaning up failed V35 migration...");
            
            // Step 1: Drop any partially created tables (in reverse dependency order)
            System.out.println("V35_1: Dropping any partially created wallet tables...");
            
            statement.execute("DROP TABLE IF EXISTS wallet_notification");
            statement.execute("DROP TABLE IF EXISTS wallet_transaction_savings_goal");
            statement.execute("DROP TABLE IF EXISTS savings_goal");
            statement.execute("DROP TABLE IF EXISTS wallet_transaction");
            statement.execute("DROP TABLE IF EXISTS child_wallet");
            statement.execute("DROP TABLE IF EXISTS expense_category");
            
            System.out.println("V35_1: Dropped any partially created tables");
            
            // Step 2: Remove the failed migration record from flyway_schema_history
            System.out.println("V35_1: Removing failed V35 migration record from flyway_schema_history...");
            
            int deleted = statement.executeUpdate(
                "DELETE FROM flyway_schema_history " +
                "WHERE version = '35' " +
                "AND success = 0"
            );
            
            if (deleted > 0) {
                System.out.println("V35_1: Removed " + deleted + " failed migration record(s)");
            }
            
            System.out.println("V35_1: Repair completed. V35_2 will now create the tables.");
        }
    }
}
