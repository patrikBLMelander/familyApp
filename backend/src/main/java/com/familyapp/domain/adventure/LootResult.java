package com.familyapp.domain.adventure;

/**
 * The outcome of claiming an adventure.
 *
 * @param type     what was won
 * @param ref      food = "food", frame = loot_item id, egg = egg_type
 * @param quantity food count; 1 for a frame or an egg
 */
public record LootResult(LootType type, String ref, int quantity) {
}
