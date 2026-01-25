-- Create collected_food table to persist food collection state
-- This allows food to persist across page reloads and sessions

CREATE TABLE collected_food (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    xp_amount INT NOT NULL DEFAULT 1,
    is_fed BOOLEAN NOT NULL DEFAULT FALSE,
    collected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fed_at DATETIME(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE
);

-- Index for finding unfed food by member
CREATE INDEX idx_collected_food_member_unfed ON collected_food(member_id, is_fed);

-- Index for finding food by event (for validation when uncompleting tasks)
CREATE INDEX idx_collected_food_event_member ON collected_food(event_id, member_id, is_fed);
