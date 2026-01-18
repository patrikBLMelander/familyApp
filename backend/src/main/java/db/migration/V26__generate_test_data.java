package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Migration to generate test data for performance testing:
 * - 50 families
 * - 2-4 members per family (1-2 PARENT, 1-2 CHILD, 0-1 ASSISTANT)
 * - Calendar events (both recurring and non-recurring)
 * - Calendar categories
 */
public class V26__generate_test_data extends BaseJavaMigration {
    
    private static final int NUM_FAMILIES = 50;
    private static final int EVENTS_PER_FAMILY = 20; // Mix of recurring and non-recurring
    private static final Random random = new Random(42); // Fixed seed for reproducibility
    
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        
        // Only generate test data if explicitly enabled via environment variable
        // This prevents test data from being generated in production
        String generateTestData = System.getenv("GENERATE_TEST_DATA");
        if (!"true".equalsIgnoreCase(generateTestData)) {
            System.out.println("Migration V26: GENERATE_TEST_DATA environment variable not set to 'true'. Skipping test data generation.");
            System.out.println("  To generate test data locally, set GENERATE_TEST_DATA=true");
            return;
        }
        
        // Check if test data already exists - if so, skip
        try (PreparedStatement checkStmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM family WHERE name LIKE 'Test Family%'")) {
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("Migration V26: Test data already exists. Skipping.");
                return;
            }
        }
        
        // Clean up any partial data from previous failed run
        System.out.println("Migration V26: Cleaning up any partial data from previous run...");
        try (PreparedStatement cleanupStmt = connection.prepareStatement(
                "DELETE FROM family WHERE name LIKE 'Test Family%'")) {
            int deleted = cleanupStmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("Migration V26: Cleaned up " + deleted + " partial families from previous run.");
            }
        }
        
        // Note: Flyway handles transactions automatically, so we don't need to manage them
        
        System.out.println("Migration V26: Generating test data...");
        System.out.println("  - " + NUM_FAMILIES + " families");
        System.out.println("  - ~" + (NUM_FAMILIES * 3) + " members");
        System.out.println("  - ~" + (NUM_FAMILIES * EVENTS_PER_FAMILY) + " calendar events");
        
        List<String> familyIds = new ArrayList<>();
        List<String> memberIds = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        
        // Create families and members
        for (int i = 1; i <= NUM_FAMILIES; i++) {
            String familyId = UUID.randomUUID().toString();
            familyIds.add(familyId);
            
            // Create family
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO family (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, familyId);
                stmt.setString(2, "Test Family " + i);
                Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());
                stmt.setTimestamp(3, now);
                stmt.setTimestamp(4, now);
                stmt.executeUpdate();
            }
            
            // Create members for this family
            int numParents = 1 + random.nextInt(2); // 1-2 parents
            int numChildren = 1 + random.nextInt(2); // 1-2 children
            int numAssistants = random.nextInt(2); // 0-1 assistants
            
            // Create parents
            for (int p = 0; p < numParents; p++) {
                createMember(connection, familyId, "PARENT", 
                    "parent" + i + "_" + p + "@test.com", "Parent " + i + "." + p, memberIds);
            }
            
            // Create children
            for (int c = 0; c < numChildren; c++) {
                createMember(connection, familyId, "CHILD", 
                    null, "Child " + i + "." + c, memberIds);
            }
            
            // Create assistants
            for (int a = 0; a < numAssistants; a++) {
                createMember(connection, familyId, "ASSISTANT", 
                    "assistant" + i + "_" + a + "@test.com", "Assistant " + i + "." + a, memberIds);
            }
            
            // Create categories for this family
            createCategory(connection, familyId, "Arbete", "#FF5733", categoryIds);
            createCategory(connection, familyId, "Skola", "#33C3F0", categoryIds);
            createCategory(connection, familyId, "Fritid", "#33FF57", categoryIds);
            createCategory(connection, familyId, "Familj", "#FF33F5", categoryIds);
            
            // Create calendar events for this family
            createCalendarEvents(connection, familyId, memberIds, categoryIds, i);
        }
        
        System.out.println("Migration V26: Test data generation complete!");
        System.out.println("  - Created " + familyIds.size() + " families");
        System.out.println("  - Created " + memberIds.size() + " members");
        System.out.println("  - Created " + categoryIds.size() + " categories");
    }
    
    private String createMember(Connection connection, String familyId, String role, 
                                String email, String name, List<String> memberIds) throws Exception {
        String memberId = UUID.randomUUID().toString();
        memberIds.add(memberId);
        
        String deviceToken = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());
        
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO family_member (id, family_id, name, role, email, device_token, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, memberId);
            stmt.setString(2, familyId);
            stmt.setString(3, name);
            stmt.setString(4, role);
            stmt.setString(5, email);
            stmt.setString(6, deviceToken);
            stmt.setTimestamp(7, now);
            stmt.setTimestamp(8, now);
            stmt.executeUpdate();
        }
        
        return memberId;
    }
    
    private String createCategory(Connection connection, String familyId, String name, 
                                  String color, List<String> categoryIds) throws Exception {
        String categoryId = UUID.randomUUID().toString();
        categoryIds.add(categoryId);
        
        Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());
        
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO calendar_event_category (id, family_id, name, color, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, categoryId);
            stmt.setString(2, familyId);
            stmt.setString(3, name);
            stmt.setString(4, color);
            stmt.setTimestamp(5, now);
            stmt.setTimestamp(6, now);
            stmt.executeUpdate();
        }
        
        return categoryId;
    }
    
    private void createCalendarEvents(Connection connection, String familyId, 
                                     List<String> memberIds, List<String> categoryIds, int familyIndex) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        
        // Get family members for this family
        List<String> familyMemberIds = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM family_member WHERE family_id = ?")) {
            stmt.setString(1, familyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                familyMemberIds.add(rs.getString("id"));
            }
        }
        
        if (familyMemberIds.isEmpty()) {
            return; // No members, skip events
        }
        
        // Get categories for this family
        List<String> familyCategoryIds = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM calendar_event_category WHERE family_id = ?")) {
            stmt.setString(1, familyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                familyCategoryIds.add(rs.getString("id"));
            }
        }
        
        if (familyCategoryIds.isEmpty()) {
            return; // No categories, skip events
        }
        
        // Create mix of recurring and non-recurring events
        for (int i = 0; i < EVENTS_PER_FAMILY; i++) {
            boolean isRecurring = random.nextDouble() < 0.4; // 40% recurring
            String eventId = UUID.randomUUID().toString();
            String categoryId = familyCategoryIds.get(random.nextInt(familyCategoryIds.size()));
            String createdById = familyMemberIds.get(random.nextInt(familyMemberIds.size()));
            
            // Event title
            String title;
            if (isRecurring) {
                String[] recurringTitles = {
                    "Veckomöte", "Träning", "Lektion", "Lunch", "Städning", 
                    "Läxor", "Frukost", "Kvällsmat", "Läsning", "Spelkväll"
                };
                title = recurringTitles[random.nextInt(recurringTitles.length)] + " " + (i + 1);
            } else {
                String[] nonRecurringTitles = {
                    "Doktorsbesök", "Födelsedag", "Konsert", "Resa", "Fest",
                    "Möte", "Evenemang", "Besök", "Aktivitet", "Utflykt"
                };
                title = nonRecurringTitles[random.nextInt(nonRecurringTitles.length)] + " " + (i + 1);
            }
            
            // Start date - mix of past, present, and future
            LocalDateTime startDateTime;
            if (random.nextDouble() < 0.3) {
                // 30% in the past (up to 30 days ago)
                startDateTime = now.minusDays(random.nextInt(30));
            } else if (random.nextDouble() < 0.7) {
                // 40% in the near future (next 30 days)
                startDateTime = now.plusDays(random.nextInt(30));
            } else {
                // 30% further in the future (30-90 days)
                startDateTime = now.plusDays(30 + random.nextInt(60));
            }
            
            // End date (1-4 hours later)
            LocalDateTime endDateTime = startDateTime.plusHours(1 + random.nextInt(3));
            
            // Recurring settings
            String recurringType = null;
            Integer recurringInterval = null;
            LocalDate recurringEndDate = null;
            Integer recurringEndCount = null;
            
            if (isRecurring) {
                String[] types = {"DAILY", "WEEKLY", "MONTHLY"};
                recurringType = types[random.nextInt(types.length)];
                recurringInterval = 1; // Every day/week/month
                
                // End after 3-12 months or 10-50 occurrences
                if (random.nextBoolean()) {
                    recurringEndDate = today.plusMonths(3 + random.nextInt(9));
                } else {
                    recurringEndCount = 10 + random.nextInt(40);
                }
            }
            
            // Task settings (20% are tasks)
            boolean isTask = random.nextDouble() < 0.2;
            Integer xpPoints = isTask ? (1 + random.nextInt(5)) : null;
            boolean isRequired = isTask && random.nextBoolean();
            
            Timestamp createdAt = Timestamp.from(OffsetDateTime.now().toInstant());
            Timestamp updatedAt = Timestamp.from(OffsetDateTime.now().toInstant());
            
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO calendar_event (" +
                    "id, family_id, category_id, title, description, " +
                    "start_datetime, end_datetime, is_all_day, location, " +
                    "created_by_id, recurring_type, recurring_interval, " +
                    "recurring_end_date, recurring_end_count, " +
                    "is_task, xp_points, is_required, " +
                    "created_at, updated_at" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                
                stmt.setString(1, eventId);
                stmt.setString(2, familyId);
                stmt.setString(3, categoryId);
                stmt.setString(4, title);
                stmt.setString(5, "Test event description for " + title);
                stmt.setTimestamp(6, Timestamp.valueOf(startDateTime));
                stmt.setTimestamp(7, Timestamp.valueOf(endDateTime));
                stmt.setBoolean(8, false);
                stmt.setString(9, null);
                stmt.setString(10, createdById);
                stmt.setString(11, recurringType);
                stmt.setObject(12, recurringInterval);
                stmt.setObject(13, recurringEndDate != null ? java.sql.Date.valueOf(recurringEndDate) : null);
                stmt.setObject(14, recurringEndCount);
                stmt.setBoolean(15, isTask);
                stmt.setObject(16, xpPoints);
                stmt.setBoolean(17, isRequired);
                stmt.setTimestamp(18, createdAt);
                stmt.setTimestamp(19, updatedAt);
                stmt.executeUpdate();
            }
        }
    }
}
