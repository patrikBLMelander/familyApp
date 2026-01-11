-- Add task fields to calendar_event table (MySQL syntax)
-- This enables calendar events to be marked as "Dagens Att Göra" tasks with XP points

ALTER TABLE calendar_event
    ADD COLUMN is_task BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE = "Dagens Att Göra", FALSE = vanlig event
    ADD COLUMN xp_points INTEGER,  -- NULL eller 0 om is_task = FALSE, annars XP-poäng
    ADD COLUMN is_required BOOLEAN NOT NULL DEFAULT TRUE;  -- TRUE = obligatorisk, FALSE = extra (only used when is_task = TRUE)

-- Add index for filtering tasks
CREATE INDEX idx_calendar_event_is_task ON calendar_event(is_task);

