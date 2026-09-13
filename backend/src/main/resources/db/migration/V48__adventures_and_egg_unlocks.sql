-- Adventures + rarity collection (post-launch feature) -- foundation only.
--
-- Everything here is additive: five new tables and two nullable columns. No existing
-- behaviour changes until the application code that reads them ships, so this migration
-- is safe to deploy ahead of that code.

-- Which eggs a child MAY pick. "Selectable" is this set minus eggs already collected
-- (pet_history) and minus the current month's pet. Commons are seeded below for every
-- existing pet-owning member; new members are covered lazily by
-- AdventureService.ensureCommonsUnlocked.
CREATE TABLE child_egg_unlock (
    id          VARCHAR(36) PRIMARY KEY,
    member_id   VARCHAR(36) NOT NULL,
    egg_type    VARCHAR(50) NOT NULL,
    source      VARCHAR(20) NOT NULL DEFAULT 'default',   -- 'default' | 'adventure'
    unlocked_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_child_egg_unlock UNIQUE (member_id, egg_type),
    CONSTRAINT fk_child_egg_unlock_member FOREIGN KEY (member_id)
        REFERENCES family_member(id) ON DELETE CASCADE
);
CREATE INDEX idx_child_egg_unlock_member ON child_egg_unlock(member_id);

-- One ticket per level cleared. UNIQUE(member_id, granted_for) makes the grant
-- idempotent: reaching L3 in a given month grants exactly one ticket, ever.
-- granted_for = 'YYYY-MM:Ln'.
CREATE TABLE adventure_ticket_grant (
    id          VARCHAR(36) PRIMARY KEY,
    member_id   VARCHAR(36) NOT NULL,
    granted_for VARCHAR(30) NOT NULL,
    granted_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_ticket_grant UNIQUE (member_id, granted_for),
    CONSTRAINT fk_ticket_grant_member FOREIGN KEY (member_id)
        REFERENCES family_member(id) ON DELETE CASCADE
);
CREATE INDEX idx_ticket_grant_member ON adventure_ticket_grant(member_id);

-- An adventure. Ready when NOW(6) >= started_at + duration_secs -- computed on the
-- server, never trusted from the client. Loot is resolved and stored here at claim time,
-- which is what makes a repeated claim idempotent and leaves an audit row.
CREATE TABLE adventure (
    id            VARCHAR(36) PRIMARY KEY,
    member_id     VARCHAR(36) NOT NULL,
    scene         VARCHAR(50) NOT NULL,
    started_at    DATETIME(6) NOT NULL,
    duration_secs INT NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ONGOING',  -- 'ONGOING' | 'CLAIMED'
    loot_type     VARCHAR(20) NULL,                        -- 'FOOD' | 'FRAME' | 'EGG'
    loot_ref      VARCHAR(50) NULL,                        -- food = 'food', frame = item id, egg = egg_type
    loot_qty      INT NULL,
    claimed_at    DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_adventure_member FOREIGN KEY (member_id)
        REFERENCES family_member(id) ON DELETE CASCADE
);
CREATE INDEX idx_adventure_member ON adventure(member_id);
CREATE INDEX idx_adventure_member_status ON adventure(member_id, status);

-- Catalog of COSMETIC loot only (frames, scene items). Food and eggs live in their own
-- systems (collected_food and child_egg_unlock), so they are not rows here. Empty until
-- the art exists; while empty, the resolver simply never rolls a frame.
CREATE TABLE loot_item (
    id        VARCHAR(50) PRIMARY KEY,          -- slug, e.g. 'frame_autumn'
    type      VARCHAR(20) NOT NULL,             -- 'FRAME' | 'SCENE_ITEM'
    rarity    VARCHAR(16) NOT NULL,             -- 'common' | 'rare' | 'epic'
    name      VARCHAR(100) NOT NULL,
    asset_key VARCHAR(100) NOT NULL,
    active    BOOLEAN NOT NULL DEFAULT TRUE
);

-- Cosmetics a child owns (frames, scene items). Own-or-not; no quantity.
CREATE TABLE child_inventory (
    id          VARCHAR(36) PRIMARY KEY,
    member_id   VARCHAR(36) NOT NULL,
    item_id     VARCHAR(50) NOT NULL,
    acquired_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_child_inventory UNIQUE (member_id, item_id),
    CONSTRAINT fk_child_inventory_member FOREIGN KEY (member_id)
        REFERENCES family_member(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_inventory_item FOREIGN KEY (item_id)
        REFERENCES loot_item(id)
);
CREATE INDEX idx_child_inventory_member ON child_inventory(member_id);

-- The frame worn on this month's scene, and the frame carried into the collection when
-- the pet retires into pet_history. Added now, used by later application code.
ALTER TABLE child_pet ADD COLUMN equipped_frame VARCHAR(50) NULL;
ALTER TABLE pet_history ADD COLUMN frame VARCHAR(50) NULL;

-- Seed the four Commons for every existing pet-owning member (CHILD and ASSISTANT, the
-- roles that own pets and pick eggs). Parents pick only inside a child's view, where the
-- member is the child, so they need no unlocks of their own.
INSERT INTO child_egg_unlock (id, member_id, egg_type, source, unlocked_at)
SELECT UUID(), fm.id, e.egg_type, 'default', NOW(6)
  FROM family_member fm
  JOIN (
      SELECT 'green_egg'  AS egg_type UNION ALL
      SELECT 'red_egg'    UNION ALL
      SELECT 'purple_egg' UNION ALL
      SELECT 'yellow_egg'
  ) e
 WHERE fm.role IN ('CHILD', 'ASSISTANT');
