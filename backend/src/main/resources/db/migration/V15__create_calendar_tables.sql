-- Calendar domain (MySQL syntax)
-- Events for family calendar with recurring support, categories, and all-day events

-- Event categories (e.g., "Skola", "Idrott", "Familj")
CREATE TABLE calendar_event_category (
    id          VARCHAR(36) PRIMARY KEY,
    family_id   VARCHAR(36) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    color       VARCHAR(7) NOT NULL DEFAULT '#b8e6b8',  -- Hex color code
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    UNIQUE KEY unique_category_name_per_family (family_id, name)
);

-- Calendar events
CREATE TABLE calendar_event (
    id                  VARCHAR(36) PRIMARY KEY,
    family_id           VARCHAR(36) NOT NULL,
    category_id         VARCHAR(36),  -- NULL = no category
    title               VARCHAR(255) NOT NULL,
    description         VARCHAR(500),
    start_datetime      DATETIME(6) NOT NULL,
    end_datetime        DATETIME(6),  -- NULL = point in time (same as start)
    is_all_day          BOOLEAN NOT NULL DEFAULT FALSE,
    location            VARCHAR(255),
    created_by_id       VARCHAR(36) NOT NULL,
    -- Recurring event fields
    recurring_type      VARCHAR(20),  -- NULL = not recurring, 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'
    recurring_interval  INTEGER DEFAULT 1,  -- Every N days/weeks/months/years
    recurring_end_date  DATE,  -- NULL = no end date
    recurring_end_count INTEGER,  -- NULL = no end count, otherwise number of occurrences
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES calendar_event_category(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by_id) REFERENCES family_member(id) ON DELETE CASCADE
);

-- Event participants (many-to-many)
CREATE TABLE calendar_event_participant (
    event_id    VARCHAR(36) NOT NULL,
    member_id   VARCHAR(36) NOT NULL,
    PRIMARY KEY (event_id, member_id),
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_calendar_event_family_id ON calendar_event(family_id);
CREATE INDEX idx_calendar_event_start_datetime ON calendar_event(start_datetime);
CREATE INDEX idx_calendar_event_category_id ON calendar_event(category_id);
CREATE INDEX idx_calendar_event_created_by_id ON calendar_event(created_by_id);
CREATE INDEX idx_calendar_event_recurring_type ON calendar_event(recurring_type);
CREATE INDEX idx_calendar_event_participant_event_id ON calendar_event_participant(event_id);
CREATE INDEX idx_calendar_event_participant_member_id ON calendar_event_participant(member_id);
CREATE INDEX idx_calendar_event_category_family_id ON calendar_event_category(family_id);

