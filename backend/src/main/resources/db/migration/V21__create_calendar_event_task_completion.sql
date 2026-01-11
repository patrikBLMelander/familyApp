-- Calendar event task completion table (MySQL syntax)
-- Tracks completion of tasks (calendar events with is_task = TRUE)

CREATE TABLE calendar_event_task_completion (
    id              VARCHAR(36) PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL,
    member_id       VARCHAR(36) NOT NULL,  -- Vem som markerade klart (för historik/spårning)
    occurrence_date DATE NOT NULL,  -- För recurring events: vilken occurrence (datum)
    completed_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_event_member_date (event_id, member_id, occurrence_date)
);

CREATE INDEX idx_calendar_event_task_completion_event_id ON calendar_event_task_completion(event_id);
CREATE INDEX idx_calendar_event_task_completion_member_id ON calendar_event_task_completion(member_id);
CREATE INDEX idx_calendar_event_task_completion_occurrence_date ON calendar_event_task_completion(occurrence_date);

-- Notera: För events med flera participants och is_task=TRUE:
-- Completion är gemensam: När NÅGON participant markerar klart, är tasken klar för ALLA participants
-- I databasen spåras completion med (event_id, member_id, occurrence_date) för att veta vem som markerade
-- Backend-logik: Check completion = finns det MINST EN completion för (event_id, occurrence_date) bland alla participants?

