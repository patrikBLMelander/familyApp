package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Migration to set a default password for existing PARENT users who don't have a password.
 * Default password: "password123"
 * Users should change this password after first login.
 */
public class V24__set_default_password_for_existing_parents extends BaseJavaMigration {
    
    private static final String DEFAULT_PASSWORD = "password123";
    
    @Override
    public void migrate(Context context) throws Exception {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        
        Connection connection = context.getConnection();
        
        // Find all PARENT users without a password
        String selectSql = "SELECT id, email, name FROM family_member " +
                          "WHERE role = 'PARENT' " +
                          "AND (password_hash IS NULL OR password_hash = '')";
        
        String updateSql = "UPDATE family_member SET password_hash = ? WHERE id = ?";
        
        try (PreparedStatement selectStmt = connection.prepareStatement(selectSql);
             PreparedStatement updateStmt = connection.prepareStatement(updateSql);
             ResultSet rs = selectStmt.executeQuery()) {
            
            int updatedCount = 0;
            while (rs.next()) {
                String memberId = rs.getString("id");
                String email = rs.getString("email");
                String name = rs.getString("name");
                
                // Generate a unique hash for each user (BCrypt uses random salt)
                String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
                
                updateStmt.setString(1, hashedPassword);
                updateStmt.setString(2, memberId);
                updateStmt.executeUpdate();
                updatedCount++;
                
                System.out.println("Set default password for user: " + name + " (" + email + ")");
            }
            
            if (updatedCount > 0) {
                System.out.println("Migration V24: Set default password for " + updatedCount + " parent user(s).");
                System.out.println("Default password: " + DEFAULT_PASSWORD);
                System.out.println("IMPORTANT: Users should change this password after first login!");
            } else {
                System.out.println("Migration V24: No parent users found without passwords.");
            }
        }
    }
}
