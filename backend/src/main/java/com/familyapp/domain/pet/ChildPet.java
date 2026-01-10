package com.familyapp.domain.pet;

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
        OffsetDateTime updatedAt
) {
    public static final int MIN_GROWTH_STAGE = 1;
    public static final int MAX_GROWTH_STAGE = 5;
}

