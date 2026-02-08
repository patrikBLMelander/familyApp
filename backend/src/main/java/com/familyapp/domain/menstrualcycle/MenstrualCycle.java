package com.familyapp.domain.menstrualcycle;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MenstrualCycle(
        UUID id,
        UUID familyMemberId,
        LocalDate periodStartDate,
        Integer periodLength,
        Integer cycleLength,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
