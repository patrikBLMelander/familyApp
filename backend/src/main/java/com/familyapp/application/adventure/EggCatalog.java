package com.familyapp.application.adventure;

import com.familyapp.domain.adventure.EggRarity;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for egg rarity.
 *
 * Deliberately a plain static class rather than a field on PetService: XpService depends
 * on AdventureService (to grant tickets on level-up), and PetService depends on XpService,
 * so having AdventureService read rarity from PetService would close a dependency cycle
 * that Spring refuses to start. The egg-to-pet mapping still lives in PetService; only the
 * rarity classification lives here, and both read the same fourteen egg types.
 */
public final class EggCatalog {

    private EggCatalog() {
    }

    private static final Map<String, EggRarity> EGG_RARITY = Map.ofEntries(
            // Common -- open from the start, the four house pets.
            Map.entry("green_egg", EggRarity.COMMON),   // cat
            Map.entry("red_egg", EggRarity.COMMON),     // dog
            Map.entry("purple_egg", EggRarity.COMMON),  // rabbit
            Map.entry("yellow_egg", EggRarity.COMMON),  // bird
            // Rare -- wild but real.
            Map.entry("orange_egg", EggRarity.RARE),    // bear
            Map.entry("black_egg", EggRarity.RARE),     // panda
            Map.entry("cyan_egg", EggRarity.RARE),      // kapybara
            Map.entry("gray_egg", EggRarity.RARE),      // sloth
            Map.entry("brown_egg", EggRarity.RARE),     // snake
            // Legendary -- apex real animals.
            Map.entry("golden_egg", EggRarity.LEGENDARY), // lion
            Map.entry("white_egg", EggRarity.LEGENDARY),  // shark
            // Mythic -- fantastical, impossible creatures.
            Map.entry("blue_egg", EggRarity.MYTHIC),   // dragon
            Map.entry("teal_egg", EggRarity.MYTHIC),   // hydra
            Map.entry("pink_egg", EggRarity.MYTHIC)    // unicorn
    );

    /** The four eggs every child starts with unlocked. */
    public static final List<String> COMMON_EGGS =
            List.of("green_egg", "red_egg", "purple_egg", "yellow_egg");

    /** Rarity of an egg type; unknown eggs default to COMMON so nothing ever looks locked
     *  by accident. */
    public static EggRarity rarityOf(String eggType) {
        return EGG_RARITY.getOrDefault(eggType, EggRarity.COMMON);
    }

    /** Egg types of a given rarity, sorted for deterministic iteration. */
    public static List<String> eggTypesOfRarity(EggRarity rarity) {
        return EGG_RARITY.entrySet().stream()
                .filter(entry -> entry.getValue() == rarity)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
