-- The first frames: cosmetic loot won on adventures. asset_key is the client drawable
-- name (Android res/drawable, iOS asset catalog). Adding a frame later is another row
-- here plus the art on both clients; nothing else changes.
INSERT INTO loot_item (id, type, rarity, name, asset_key, active) VALUES
    ('frame_forest',  'FRAME', 'common', 'Skogsram',      'frame_forest',  TRUE),
    ('frame_night',   'FRAME', 'rare',   'Natthimmel',    'frame_night',   TRUE),
    ('frame_reef',    'FRAME', 'rare',   'Korallrev',     'frame_reef',    TRUE),
    ('frame_royal',   'FRAME', 'epic',   'Skattkammare',  'frame_royal',   TRUE),
    ('frame_crystal', 'FRAME', 'epic',   'Kristall',      'frame_crystal', TRUE);
