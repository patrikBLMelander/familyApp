-- Pet history table (MySQL syntax)
-- Stores historical pet data after monthly reset
-- This allows children to see their previous month's pets

CREATE TABLE pet_history (
    id                  VARCHAR(36) PRIMARY KEY,
    member_id           VARCHAR(36)  NOT NULL,
    year                INTEGER      NOT NULL,
    month               INTEGER      NOT NULL, -- 1-12
    selected_egg_type   VARCHAR(50)  NOT NULL, -- The egg type that was selected
    pet_type            VARCHAR(50)  NOT NULL, -- The pet type that hatched
    final_growth_stage  INTEGER      NOT NULL, -- Final growth stage reached (1-5)
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE,
    UNIQUE KEY unique_pet_history_month (member_id, year, month)
);

CREATE INDEX idx_pet_history_member_id ON pet_history(member_id);
CREATE INDEX idx_pet_history_year_month ON pet_history(year, month);

