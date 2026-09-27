package com.familyapp.domain.pet;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChildPet(
        UUID id,
        UUID memberId,
        int year,
        int month, // 1-12
        String selectedEggType, // e.g., "blue_egg", "green_egg", "red_egg", "yellow_egg", "purple_egg"
        String petType, // e.g., "dragon", "cat", "dog", "bird", "rabbit"
        String name, // Pet's name (chosen by the child)
        int growthStage, // 1-5 (calculated from XP/level)
        OffsetDateTime hatchedAt, // NULL until egg is hatched
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String equippedFrame, // loot_item id of the frame on this month's scene, or null
        String equippedSceneItem // loot_item id of the scene decoration, or null
) {
    public static final int MIN_GROWTH_STAGE = 1;
    public static final int MAX_GROWTH_STAGE = 5;

    /**
     * A child's first pet that hatches in this many last days of a month follows them
     * through the whole next month, so joining on the 29th doesn't mean two days of pet.
     */
    public static final int FIRST_PET_GRACE_DAYS = 10;

    /** True when the date falls in the last {@link #FIRST_PET_GRACE_DAYS} days of its month. */
    public static boolean inFirstPetGraceWindow(LocalDate date) {
        return date.getDayOfMonth() > date.lengthOfMonth() - FIRST_PET_GRACE_DAYS;
    }
}

