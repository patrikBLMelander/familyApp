-- Scene decorations: cosmetic loot layered onto the pet's scene, anchored to the sky
-- (top) or the ground (bottom). Additive.

-- The decoration on this month's scene, separate from the frame.
ALTER TABLE child_pet ADD COLUMN equipped_scene_item VARCHAR(50) NULL;

-- Where a scene item sits on the band. Null for frames (they surround the portrait, not
-- the scene).
ALTER TABLE loot_item ADD COLUMN anchor VARCHAR(16) NULL; -- 'top' | 'bottom'

-- The first four decorations. asset_key is the client drawable / imageset name.
INSERT INTO loot_item (id, type, rarity, name, asset_key, active, anchor) VALUES
    ('item_kite',         'SCENE_ITEM', 'common', 'Drake',      'item_kite',         TRUE, 'top'),
    ('item_lanterns',     'SCENE_ITEM', 'rare',   'Lyktor',     'item_lanterns',     TRUE, 'top'),
    ('item_shootingstar', 'SCENE_ITEM', 'epic',   'Stjärnfall', 'item_shootingstar', TRUE, 'top'),
    ('item_fountain',     'SCENE_ITEM', 'rare',   'Fontän',     'item_fountain',     TRUE, 'bottom');
