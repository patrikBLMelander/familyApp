-- Migrate existing recurring weekly calendar tasks to the daily_chore system.
--
-- The old system created one calendar_event per weekday for a recurring task
-- (e.g. "Borsta tänderna" on MON + "Borsta tänderna" on WED = 2 events).
-- daily_chore stores all weekdays in a single row as "MON,WED".
-- This migration groups events by (family_id, member_id, title) and combines
-- their weekdays using GROUP_CONCAT, then inserts one daily_chore per group.
--
-- Only processes events where:
--   is_task = TRUE AND recurring_type = 'WEEKLY'
-- Skips any (member_id, title) combination already present in daily_chore
-- to make re-runs safe.

INSERT INTO daily_chore (id, family_id, member_id, title, weekdays, xp_points, is_active, created_at, updated_at)
SELECT
    UUID(),
    ce.family_id,
    cep.member_id,
    ce.title,
    GROUP_CONCAT(
        CASE DAYOFWEEK(ce.start_datetime)
            WHEN 1 THEN 'SUN'
            WHEN 2 THEN 'MON'
            WHEN 3 THEN 'TUE'
            WHEN 4 THEN 'WED'
            WHEN 5 THEN 'THU'
            WHEN 6 THEN 'FRI'
            WHEN 7 THEN 'SAT'
        END
        ORDER BY FIELD(DAYOFWEEK(ce.start_datetime), 2, 3, 4, 5, 6, 7, 1)
        SEPARATOR ','
    ) AS weekdays,
    COALESCE(MAX(ce.xp_points), 1) AS xp_points,
    TRUE,
    NOW(6),
    NOW(6)
FROM calendar_event ce
JOIN calendar_event_participant cep ON ce.id = cep.event_id
WHERE ce.is_task = TRUE
  AND ce.recurring_type = 'WEEKLY'
  AND NOT EXISTS (
      SELECT 1
      FROM daily_chore dc
      WHERE dc.member_id = cep.member_id
        AND dc.title    = ce.title
  )
GROUP BY ce.family_id, cep.member_id, ce.title;
