-- Calendar event exceptions table (MySQL syntax)
-- Tracks excluded or modified occurrences of recurring events
-- 
-- For DELETE operations:
--   - Stores occurrence_date that should be excluded from recurring series
--   - When generating instances, skip occurrences that have exceptions
--
-- For EDIT operations:
--   - Stores occurrence_date that should be excluded (original occurrence)
--   - A separate event is created for the modified occurrence
--   - The exception links the original event to the modified event

CREATE TABLE calendar_event_exception (
    id                  VARCHAR(36) PRIMARY KEY,
    event_id            VARCHAR(36) NOT NULL,  -- Original recurring event
    occurrence_date     DATE NOT NULL,  -- Which occurrence to exclude/modify
    modified_event_id   VARCHAR(36) NULL,  -- If editing: link to new separate event
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    FOREIGN KEY (modified_event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    UNIQUE KEY unique_event_occurrence (event_id, occurrence_date)
);

CREATE INDEX idx_calendar_event_exception_event_id ON calendar_event_exception(event_id);
CREATE INDEX idx_calendar_event_exception_occurrence_date ON calendar_event_exception(occurrence_date);
CREATE INDEX idx_calendar_event_exception_modified_event_id ON calendar_event_exception(modified_event_id);
