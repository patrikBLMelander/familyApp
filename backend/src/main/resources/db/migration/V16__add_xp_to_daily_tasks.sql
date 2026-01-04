-- Add XP and required flag to daily tasks (MySQL syntax)
-- XP system for children to earn points and levels

-- Add is_required and xp_points to daily_task table
ALTER TABLE daily_task
    ADD COLUMN is_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN xp_points INTEGER NOT NULL DEFAULT 10;

-- Create member XP progress table for current month
-- This tracks the current month's XP and level for each member
CREATE TABLE member_xp_progress (
    id                  VARCHAR(36) PRIMARY KEY,
    member_id           VARCHAR(36)  NOT NULL,
    year                INTEGER      NOT NULL,
    month               INTEGER      NOT NULL, -- 1-12
    current_xp          INTEGER      NOT NULL DEFAULT 0,
    current_level       INTEGER      NOT NULL DEFAULT 1, -- 1-10
    total_tasks_completed INTEGER    NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_member_month (member_id, year, month)
);

CREATE INDEX idx_member_xp_progress_member_id ON member_xp_progress(member_id);
CREATE INDEX idx_member_xp_progress_year_month ON member_xp_progress(year, month);

-- Create member XP history table for past months
-- This stores historical data after monthly reset
CREATE TABLE member_xp_history (
    id                  VARCHAR(36) PRIMARY KEY,
    member_id           VARCHAR(36)  NOT NULL,
    year                INTEGER      NOT NULL,
    month               INTEGER      NOT NULL, -- 1-12
    final_xp            INTEGER      NOT NULL,
    final_level         INTEGER      NOT NULL, -- 1-10
    total_tasks_completed INTEGER    NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_member_history_month (member_id, year, month)
);

CREATE INDEX idx_member_xp_history_member_id ON member_xp_history(member_id);
CREATE INDEX idx_member_xp_history_year_month ON member_xp_history(year, month);

