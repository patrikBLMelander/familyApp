-- Scene decorations are now sky/flying objects only. Ground-anchored decorations had to
-- match the terrain of each scene (a fountain in the river, on snow, etc. looked wrong),
-- while the top of almost every scene is just sky -- so a kite, lanterns or a shooting star
-- always sit cleanly. Remove the one ground item (item_fountain); the sky items remain.

-- Un-equip it from anyone wearing it, so no pet keeps a now-removed decoration.
UPDATE child_pet SET equipped_scene_item = NULL WHERE equipped_scene_item = 'item_fountain';

-- Clear it from inventories (FK child_inventory.item_id -> loot_item.id) before deleting.
DELETE FROM child_inventory WHERE item_id = 'item_fountain';

-- Remove the catalog entry.
DELETE FROM loot_item WHERE id = 'item_fountain';
