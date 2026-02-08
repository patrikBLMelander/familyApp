-- Add menstrual cycle tracking to family members
-- Only adults (PARENT, ASSISTANT) can enable menstrual cycle tracking

-- Add settings to family_member table
ALTER TABLE family_member ADD COLUMN menstrual_cycle_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE family_member ADD COLUMN menstrual_cycle_private BOOLEAN DEFAULT TRUE;

-- Create menstrual_cycle table to store period entries
CREATE TABLE menstrual_cycle (
    id              VARCHAR(36) PRIMARY KEY,
    family_member_id VARCHAR(36) NOT NULL,
    period_start_date DATE NOT NULL,
    period_length   INT DEFAULT 5,
    cycle_length    INT,  -- Calculated automatically based on previous entry
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (family_member_id) REFERENCES family_member(id) ON DELETE CASCADE
);

-- Index for efficient queries
CREATE INDEX idx_menstrual_cycle_member_id ON menstrual_cycle(family_member_id);
CREATE INDEX idx_menstrual_cycle_period_start ON menstrual_cycle(period_start_date);
