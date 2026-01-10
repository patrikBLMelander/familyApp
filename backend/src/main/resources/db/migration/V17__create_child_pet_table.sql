-- Child pet system (MySQL syntax)
-- Pets that children care for by completing tasks (connected to XP system)
-- Each child gets a pet per month, chosen from an egg

CREATE TABLE child_pet (
    id                  VARCHAR(36) PRIMARY KEY,
    member_id           VARCHAR(36)  NOT NULL,
    year                INTEGER      NOT NULL,
    month               INTEGER      NOT NULL, -- 1-12
    selected_egg_type   VARCHAR(50)  NOT NULL, -- e.g., "blue_egg", "green_egg", "red_egg", "yellow_egg", "purple_egg"
    pet_type            VARCHAR(50)  NOT NULL, -- e.g., "dragon", "cat", "dog", "bird", "rabbit" (determined from egg type)
    growth_stage        INTEGER      NOT NULL DEFAULT 1, -- 1-5 (calculated from XP/level)
    hatched_at          DATETIME(6), -- NULL until egg is hatched (after selection)
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_child_pet_month (member_id, year, month)
);

CREATE INDEX idx_child_pet_member_id ON child_pet(member_id);
CREATE INDEX idx_child_pet_year_month ON child_pet(year, month);

