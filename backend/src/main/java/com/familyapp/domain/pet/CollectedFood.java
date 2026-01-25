package com.familyapp.domain.pet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectedFood(
        UUID id,
        UUID memberId,
        UUID eventId, // null for bonus food
        int xpAmount,
        boolean isFed,
        OffsetDateTime collectedAt,
        OffsetDateTime fedAt
) {
}
