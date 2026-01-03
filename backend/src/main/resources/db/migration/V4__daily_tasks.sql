-- Daily tasks domain (MySQL syntax)
-- Tasks that children need to complete before screen time

CREATE TABLE daily_task (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    monday      BOOLEAN      NOT NULL DEFAULT FALSE,
    tuesday     BOOLEAN      NOT NULL DEFAULT FALSE,
    wednesday   BOOLEAN      NOT NULL DEFAULT FALSE,
    thursday    BOOLEAN      NOT NULL DEFAULT FALSE,
    friday      BOOLEAN      NOT NULL DEFAULT FALSE,
    saturday    BOOLEAN      NOT NULL DEFAULT FALSE,
    sunday      BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE daily_task_completion (
    id          VARCHAR(36) PRIMARY KEY,
    task_id     VARCHAR(36)  NOT NULL,
    completed_date DATE      NOT NULL,
    completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (task_id) REFERENCES daily_task (id) ON DELETE CASCADE,
    UNIQUE KEY unique_task_date (task_id, completed_date)
);

CREATE INDEX idx_daily_task_position ON daily_task (position);
CREATE INDEX idx_daily_task_completion_task_id ON daily_task_completion (task_id);
CREATE INDEX idx_daily_task_completion_date ON daily_task_completion (completed_date);

