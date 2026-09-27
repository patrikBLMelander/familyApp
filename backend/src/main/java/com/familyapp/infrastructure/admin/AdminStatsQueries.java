package com.familyapp.infrastructure.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate queries behind the admin statistics page.
 *
 * Deliberately plain SQL over the tables the app already writes: the page counts what
 * exists, it adds no tracking. Times are the database's DATETIME values, which are UTC.
 * Everything is reduced to one row per family (or to counts) here, and the arithmetic
 * lives in AdminStatsService where it can be tested without a database.
 */
@Repository
public class AdminStatsQueries {

    private static final String CHILD_ROLES = "('CHILD','ASSISTANT')";

    private final JdbcTemplate jdbc;

    public AdminStatsQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One family, reduced to the facts the page counts. No names, no ids leave this layer. */
    public record FamilyRow(
            LocalDateTime createdAt,
            String currency,
            int children,
            int adults,
            boolean childPhoneLinked,
            boolean firstEggPicked,
            boolean anyChoreDone,
            boolean choreDoneAfterFirstWeek,
            boolean activeLast7Days,
            boolean referred,
            String subscriptionStatus,
            boolean comped,
            LocalDateTime trialEndsAt,
            String platform,
            boolean autoAllowance
    ) {}

    /** Every family with its facts. {@code activeSince} is the start of the 7-day window. */
    public List<FamilyRow> families(LocalDateTime activeSince) {
        var sql = """
                SELECT f.created_at, f.currency,
                  (SELECT COUNT(*) FROM family_member m WHERE m.family_id = f.id AND m.role IN %1$s) AS children,
                  (SELECT COUNT(*) FROM family_member m WHERE m.family_id = f.id AND m.role = 'PARENT') AS adults,
                  EXISTS (SELECT 1 FROM family_member m WHERE m.family_id = f.id AND m.role IN %1$s
                          AND m.device_token IS NOT NULL AND m.device_token <> '') AS child_linked,
                  (EXISTS (SELECT 1 FROM child_pet p JOIN family_member m ON m.id = p.member_id
                           WHERE m.family_id = f.id AND m.role IN %1$s)
                   OR EXISTS (SELECT 1 FROM pet_history h JOIN family_member m ON m.id = h.member_id
                              WHERE m.family_id = f.id AND m.role IN %1$s)) AS egg_picked,
                  EXISTS (SELECT 1 FROM daily_chore_completion c JOIN family_member m ON m.id = c.member_id
                          WHERE m.family_id = f.id) AS any_done,
                  EXISTS (SELECT 1 FROM daily_chore_completion c JOIN family_member m ON m.id = c.member_id
                          WHERE m.family_id = f.id AND c.completed_at >= DATE_ADD(f.created_at, INTERVAL 7 DAY)) AS done_after_week,
                  EXISTS (SELECT 1 FROM daily_chore_completion c JOIN family_member m ON m.id = c.member_id
                          WHERE m.family_id = f.id AND c.completed_at >= ?) AS active_7d,
                  EXISTS (SELECT 1 FROM affiliate_referral r WHERE r.family_id = f.id) AS referred,
                  s.status, s.is_comped, s.trial_ends_at, s.platform,
                  EXISTS (SELECT 1 FROM recurring_allowance a JOIN family_member m ON m.id = a.member_id
                          WHERE m.family_id = f.id AND a.active = TRUE) AS auto_allowance
                FROM family f
                LEFT JOIN family_subscription s ON s.family_id = f.id
                """.formatted(CHILD_ROLES);
        return jdbc.query(sql, (rs, i) -> new FamilyRow(
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("currency"),
                rs.getInt("children"),
                rs.getInt("adults"),
                rs.getBoolean("child_linked"),
                rs.getBoolean("egg_picked"),
                rs.getBoolean("any_done"),
                rs.getBoolean("done_after_week"),
                rs.getBoolean("active_7d"),
                rs.getBoolean("referred"),
                rs.getString("status"),
                rs.getBoolean("is_comped"),
                rs.getObject("trial_ends_at", LocalDateTime.class),
                rs.getString("platform"),
                rs.getBoolean("auto_allowance")
        ), activeSince);
    }

    /** The saved language of every adult; null means the app follows the phone. */
    public List<String> adultLanguages() {
        return jdbc.queryForList("SELECT language FROM family_member WHERE role = 'PARENT'", String.class);
    }

    /** A family being active in a given hour (UTC): enough to bucket into local weeks. */
    public record ActivityRow(String familyKey, LocalDateTime hour) {}

    /** Distinct (family, hour) pairs with a completed chore since {@code since}. */
    public List<ActivityRow> activityByHour(LocalDateTime since) {
        var sql = """
                SELECT DISTINCT m.family_id AS family_key,
                  DATE_FORMAT(c.completed_at, '%Y-%m-%d %H:00:00') AS hour
                FROM daily_chore_completion c JOIN family_member m ON m.id = c.member_id
                WHERE c.completed_at >= ?
                """;
        return jdbc.query(sql, (rs, i) -> new ActivityRow(
                rs.getString("family_key"),
                LocalDateTime.parse(rs.getString("hour").replace(' ', 'T'))
        ), since);
    }

    /** Completed chores since {@code since}. */
    public long completionsSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM daily_chore_completion WHERE completed_at >= ?", since);
    }

    /** Children with at least one active chore. */
    public long childrenWithActiveChore() {
        return count("SELECT COUNT(DISTINCT d.member_id) FROM daily_chore d JOIN family_member m ON m.id = d.member_id "
                + "WHERE d.is_active = TRUE AND m.role IN " + CHILD_ROLES);
    }

    /** A child's XP this month. */
    public record XpRow(int level, int xp) {}

    /** XP progress rows for children in the given month. */
    public List<XpRow> childXp(int year, int month) {
        return jdbc.query("SELECT p.current_level, p.current_xp FROM member_xp_progress p "
                        + "JOIN family_member m ON m.id = p.member_id "
                        + "WHERE p.year = ? AND p.month = ? AND m.role IN " + CHILD_ROLES,
                (rs, i) -> new XpRow(rs.getInt(1), rs.getInt(2)), year, month);
    }

    /** Adventures started since {@code since}. */
    public long adventuresSince(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM adventure WHERE started_at >= ?", since);
    }

    /** Children with an active savings goal that has not been bought yet. */
    public long childrenWithSavingsGoal() {
        return count("SELECT COUNT(DISTINCT g.member_id) FROM savings_goal g JOIN family_member m ON m.id = g.member_id "
                + "WHERE g.is_active = TRUE AND g.is_purchased = FALSE AND m.role IN " + CHILD_ROLES);
    }

    /** A stored store webhook: its type and the raw body, for the price. */
    public record EventRow(String type, String payload) {}

    /** Purchase-related subscription events received since {@code since}. */
    public List<EventRow> subscriptionEvents(LocalDateTime since) {
        return jdbc.query("SELECT event_type, payload FROM subscription_event WHERE received_at >= ? "
                        + "AND event_type IN ('INITIAL_PURCHASE','RENEWAL','CANCELLATION','BILLING_ISSUE')",
                (rs, i) -> new EventRow(rs.getString(1), rs.getString(2)), since);
    }

    /** Referred families that pay, per affiliate id. */
    public Map<String, Long> payingReferralsByAffiliate() {
        var result = new HashMap<String, Long>();
        jdbc.query("SELECT r.affiliate_id, COUNT(*) FROM affiliate_referral r "
                        + "JOIN family_subscription s ON s.family_id = r.family_id "
                        + "WHERE s.status = 'ACTIVE' AND s.is_comped = FALSE GROUP BY r.affiliate_id",
                rs -> { result.put(rs.getString(1), rs.getLong(2)); });
        return result;
    }

    private long count(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
