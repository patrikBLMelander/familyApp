-- Migration script to migrate daily_task to calendar_event
-- 
-- STRATEGY:
-- For each daily_task:
--   1. Get family_id from daily_task.family_id (must exist, skip if NULL)
--   2. Get members: either from daily_task_member OR all members in family (if no members linked)
--   3. For each (task, member, active_day) combination:
--      - Create calendar_event with WEEKLY recurring pattern
--      - Add member as participant
--      - Store mapping for completion migration
--   4. Migrate completions from daily_task_completion to calendar_event_task_completion
--
-- EDGE CASES HANDLED:
-- - Tasks with no family_id: SKIPPED
-- - Tasks with no members linked: Uses all members in the task's family
-- - Tasks with NULL xp_points: Uses default value 1
-- - Completions for days that don't match the task's active days: SKIPPED (validated)
-- - Multiple completions for same (task, member, date): Only first one migrated (via NOT EXISTS check)

-- ============================================================================
-- STEP 1: Create temporary mapping table to track (task_id, member_id, day) -> event_id
-- ============================================================================
CREATE TEMPORARY TABLE daily_task_event_mapping (
    task_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    day_of_week INT NOT NULL, -- 1=Monday, 2=Tuesday, ..., 7=Sunday
    event_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (task_id, member_id, day_of_week),
    INDEX idx_event_id (event_id),
    INDEX idx_task_member (task_id, member_id)
) ENGINE=InnoDB;

-- ============================================================================
-- STEP 2: Migration using stored procedure for complex logic
-- ============================================================================
DELIMITER //

CREATE PROCEDURE migrate_daily_tasks_to_calendar_events()
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_task_id VARCHAR(36);
    DECLARE v_task_name VARCHAR(255);
    DECLARE v_task_description VARCHAR(500);
    DECLARE v_family_id VARCHAR(36);
    DECLARE v_xp_points INT;
    DECLARE v_is_required BOOLEAN;
    DECLARE v_created_at DATETIME(6);
    DECLARE v_updated_at DATETIME(6);
    DECLARE v_monday BOOLEAN;
    DECLARE v_tuesday BOOLEAN;
    DECLARE v_wednesday BOOLEAN;
    DECLARE v_thursday BOOLEAN;
    DECLARE v_friday BOOLEAN;
    DECLARE v_saturday BOOLEAN;
    DECLARE v_sunday BOOLEAN;
    DECLARE v_member_id VARCHAR(36);
    DECLARE v_day_num INT;
    DECLARE v_mysql_day INT;
    DECLARE v_next_date DATE;
    DECLARE v_next_datetime DATETIME(6);
    DECLARE v_event_id VARCHAR(36);
    DECLARE v_created_by_id VARCHAR(36);
    
    -- Cursor for all tasks with family_id
    DECLARE task_cursor CURSOR FOR 
        SELECT id, name, description, family_id, 
               COALESCE(xp_points, 1) as xp_points, 
               COALESCE(is_required, TRUE) as is_required, 
               created_at, updated_at,
               monday, tuesday, wednesday, thursday, friday, saturday, sunday
        FROM daily_task
        WHERE family_id IS NOT NULL;
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
    
    OPEN task_cursor;
    
    read_task_loop: LOOP
        SET v_done = FALSE; -- Reset for each iteration
        FETCH task_cursor INTO v_task_id, v_task_name, v_task_description, v_family_id, 
                              v_xp_points, v_is_required, v_created_at, v_updated_at,
                              v_monday, v_tuesday, v_wednesday, v_thursday, v_friday, v_saturday, v_sunday;
        
        IF v_done THEN
            LEAVE read_task_loop;
        END IF;
        
        -- Get created_by_id: first parent in family, or first member if no parent
        SELECT id INTO v_created_by_id
        FROM family_member
        WHERE family_id = v_family_id
        AND (role = 'PARENT' OR role IS NULL)
        ORDER BY created_at ASC
        LIMIT 1;
        
        IF v_created_by_id IS NULL THEN
            SELECT id INTO v_created_by_id
            FROM family_member
            WHERE family_id = v_family_id
            ORDER BY created_at ASC
            LIMIT 1;
        END IF;
        
        -- Skip if no members in family (should not happen, but safe)
        IF v_created_by_id IS NULL THEN
            ITERATE read_task_loop;
        END IF;
        
        -- Process each member for this task
        -- Members: either from daily_task_member OR all members in family (if no members linked)
        BLOCK1: BEGIN
            DECLARE v_member_done INT DEFAULT FALSE;
            DECLARE member_cursor CURSOR FOR
                SELECT DISTINCT COALESCE(dtm.member_id, fm.id) as member_id
                FROM family_member fm
                LEFT JOIN daily_task_member dtm ON dtm.task_id = v_task_id AND dtm.member_id = fm.id
                WHERE fm.family_id = v_family_id
                AND (
                    dtm.member_id IS NOT NULL 
                    OR NOT EXISTS (SELECT 1 FROM daily_task_member dtm2 WHERE dtm2.task_id = v_task_id)
                );
            DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_member_done = TRUE;
            
            SET v_member_done = FALSE; -- Reset for this task
            OPEN member_cursor;
            
            read_member_loop: LOOP
                FETCH member_cursor INTO v_member_id;
                
                IF v_member_done THEN
                    LEAVE read_member_loop;
                END IF;
                
                -- Process each day of week (1=Monday through 7=Sunday)
                SET v_day_num = 1;
                WHILE v_day_num <= 7 DO
                    -- Check if this day is active for this task
                    IF (v_day_num = 1 AND v_monday = TRUE) OR
                       (v_day_num = 2 AND v_tuesday = TRUE) OR
                       (v_day_num = 3 AND v_wednesday = TRUE) OR
                       (v_day_num = 4 AND v_thursday = TRUE) OR
                       (v_day_num = 5 AND v_friday = TRUE) OR
                       (v_day_num = 6 AND v_saturday = TRUE) OR
                       (v_day_num = 7 AND v_sunday = TRUE) THEN
                        
                        -- Calculate MySQL DAYOFWEEK (1=Sunday, 2=Monday, ..., 7=Saturday)
                        -- Our day: 1=Monday, 2=Tuesday, ..., 7=Sunday
                        SET v_mysql_day = CASE 
                            WHEN v_day_num = 1 THEN 2  -- Monday
                            WHEN v_day_num = 2 THEN 3  -- Tuesday
                            WHEN v_day_num = 3 THEN 4  -- Wednesday
                            WHEN v_day_num = 4 THEN 5  -- Thursday
                            WHEN v_day_num = 5 THEN 6  -- Friday
                            WHEN v_day_num = 6 THEN 7  -- Saturday
                            WHEN v_day_num = 7 THEN 1  -- Sunday
                        END;
                        
                        -- Calculate next occurrence of this weekday
                        -- Formula: (target_day - current_day + 7) % 7
                        -- If result is 0 (today is that day), add 7 to get next week
                        SET v_next_date = DATE_ADD(CURDATE(), 
                            INTERVAL (
                                CASE 
                                    WHEN (v_mysql_day - DAYOFWEEK(CURDATE()) + 7) % 7 = 0 
                                    THEN 7
                                    ELSE (v_mysql_day - DAYOFWEEK(CURDATE()) + 7) % 7
                                END
                            ) DAY);
                        
                        SET v_next_datetime = CAST(v_next_date AS DATETIME(6));
                        
                        -- Generate event ID
                        SET v_event_id = UUID();
                        
                        -- Insert calendar_event
                        INSERT INTO calendar_event (
                            id, family_id, category_id, title, description,
                            start_datetime, end_datetime, is_all_day, location, created_by_id,
                            recurring_type, recurring_interval, recurring_end_date, recurring_end_count,
                            is_task, xp_points, is_required, created_at, updated_at
                        ) VALUES (
                            v_event_id, v_family_id, NULL, v_task_name, v_task_description,
                            v_next_datetime, v_next_datetime, TRUE, NULL, v_created_by_id,
                            'WEEKLY', 1, NULL, NULL,
                            TRUE, v_xp_points, v_is_required, v_created_at, v_updated_at
                        );
                        
                        -- Insert participant
                        INSERT INTO calendar_event_participant (event_id, member_id)
                        VALUES (v_event_id, v_member_id);
                        
                        -- Store mapping for completion migration
                        INSERT INTO daily_task_event_mapping (task_id, member_id, day_of_week, event_id)
                        VALUES (v_task_id, v_member_id, v_day_num, v_event_id);
                        
                    END IF;
                    
                    SET v_day_num = v_day_num + 1;
                END WHILE;
                
            END LOOP;
            
            CLOSE member_cursor;
        END BLOCK1;
        
    END LOOP;
    
    CLOSE task_cursor;
    
END//

DELIMITER ;

-- Execute the migration procedure
CALL migrate_daily_tasks_to_calendar_events();

-- Drop the procedure
DROP PROCEDURE IF EXISTS migrate_daily_tasks_to_calendar_events;

-- ============================================================================
-- STEP 3: Migrate completions from daily_task_completion to calendar_event_task_completion
-- ============================================================================
INSERT INTO calendar_event_task_completion (
    id,
    event_id,
    member_id,
    occurrence_date,
    completed_at
)
SELECT
    UUID() as id,
    m.event_id,
    dtc.member_id,
    dtc.completed_date,
    dtc.completed_at
FROM daily_task_completion dtc
INNER JOIN daily_task_event_mapping m ON 
    m.task_id = dtc.task_id AND 
    m.member_id = dtc.member_id AND
    m.day_of_week = CASE 
        WHEN DAYOFWEEK(dtc.completed_date) = 2 THEN 1 -- Monday
        WHEN DAYOFWEEK(dtc.completed_date) = 3 THEN 2 -- Tuesday
        WHEN DAYOFWEEK(dtc.completed_date) = 4 THEN 3 -- Wednesday
        WHEN DAYOFWEEK(dtc.completed_date) = 5 THEN 4 -- Thursday
        WHEN DAYOFWEEK(dtc.completed_date) = 6 THEN 5 -- Friday
        WHEN DAYOFWEEK(dtc.completed_date) = 7 THEN 6 -- Saturday
        WHEN DAYOFWEEK(dtc.completed_date) = 1 THEN 7 -- Sunday
    END
WHERE NOT EXISTS (
    -- Avoid duplicates: check if completion already exists
    SELECT 1 FROM calendar_event_task_completion cetc
    WHERE cetc.event_id = m.event_id
    AND cetc.member_id = dtc.member_id
    AND cetc.occurrence_date = dtc.completed_date
)
-- Only migrate completions that match the task's active days (safety check)
AND EXISTS (
    SELECT 1 FROM daily_task dt
    WHERE dt.id = dtc.task_id
    AND (
        (DAYOFWEEK(dtc.completed_date) = 2 AND dt.monday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 3 AND dt.tuesday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 4 AND dt.wednesday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 5 AND dt.thursday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 6 AND dt.friday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 7 AND dt.saturday = TRUE) OR
        (DAYOFWEEK(dtc.completed_date) = 1 AND dt.sunday = TRUE)
    )
);

-- Drop temporary mapping table (will be dropped automatically at end of session, but explicit is better)
DROP TEMPORARY TABLE IF EXISTS daily_task_event_mapping;

