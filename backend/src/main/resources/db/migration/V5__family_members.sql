-- Family members domain (MySQL syntax)
-- Represents children/family members in the family

CREATE TABLE family_member (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    device_token VARCHAR(255) UNIQUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- Many-to-many relationship between daily tasks and family members
-- If a task has no members, it applies to all members
CREATE TABLE daily_task_member (
    task_id    VARCHAR(36) NOT NULL,
    member_id  VARCHAR(36) NOT NULL,
    PRIMARY KEY (task_id, member_id),
    FOREIGN KEY (task_id) REFERENCES daily_task (id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member (id) ON DELETE CASCADE
);

CREATE INDEX idx_daily_task_member_task_id ON daily_task_member (task_id);
CREATE INDEX idx_daily_task_member_member_id ON daily_task_member (member_id);

