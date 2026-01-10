package com.familyapp.domain.pet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PetHistory(
        UUID id,
        UUID memberId,
        int year,
        int month, // 1-12
        String selectedEggType, // The egg type that was selected
        String petType, // The pet type that hatched
        int finalGrowthStage, // Final growth stage reached (1-5)
        OffsetDateTime createdAt
) {
}

