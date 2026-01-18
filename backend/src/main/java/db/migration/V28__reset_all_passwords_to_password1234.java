package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Migration to reset all passwords for PARENT and ASSISTANT users to "Password1234".
 * This is for testing/debugging purposes to ensure known password for login testing.
 */
public class V28__reset_all_passwords_to_password1234 extends BaseJavaMigration {
    
    private static final String RESET_PASSWORD = "Password1234";
    
    @Override
    public void migrate(Context context) throws Exception {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        
        Connection connection = context.getConnection();
        
        // Generate a unique hash for the reset password (BCrypt uses random salt)
        String hashedPassword = passwordEncoder.encode(RESET_PASSWORD);
        
        // Update all PARENT and ASSISTANT users with the new password
        // This includes users who don't have a password set yet
        String updateSql = "UPDATE family_member " +
                          "SET password_hash = ? " +
                          "WHERE role IN ('PARENT', 'ASSISTANT')";
        
        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
            updateStmt.setString(1, hashedPassword);
            int updatedRows = updateStmt.executeUpdate();
            
            System.out.println("Reset passwords for " + updatedRows + " users (PARENT and ASSISTANT) to: " + RESET_PASSWORD);
        }
    }
}
