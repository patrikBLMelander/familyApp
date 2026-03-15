-- Daily chore system: recurring tasks stored by weekday pattern
-- More efficient than calendar events for recurring chores
-- Old calendar-based chores remain untouched (shown alongside these)

CREATE TABLE daily_chore (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    weekdays VARCHAR(50) NOT NULL, -- e.g. "MON,TUE,WED,THU,FRI"
    xp_points INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

CREATE TABLE daily_chore_completion (
    id VARCHAR(36) PRIMARY KEY,
    chore_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    occurrence_date DATE NOT NULL,
    completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_chore_completion (chore_id, member_id, occurrence_date),
    FOREIGN KEY (chore_id) REFERENCES daily_chore(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

CREATE INDEX idx_daily_chore_member ON daily_chore(member_id, is_active);
CREATE INDEX idx_daily_chore_family ON daily_chore(family_id);
CREATE INDEX idx_daily_chore_completion_chore ON daily_chore_completion(chore_id, occurrence_date);
CREATE INDEX idx_daily_chore_completion_member ON daily_chore_completion(member_id, occurrence_date);
