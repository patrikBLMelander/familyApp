package com.familyapp.domain.chore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyChore(
        UUID id,
        UUID familyId,
        UUID memberId,
        String title,
        List<String> weekdays,
        int xpPoints,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
