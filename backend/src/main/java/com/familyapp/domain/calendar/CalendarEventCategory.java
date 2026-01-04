package com.familyapp.domain.calendar;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarEventCategory(
        UUID id,
        UUID familyId,
        String name,
        String color,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

