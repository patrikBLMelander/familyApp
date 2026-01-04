package com.familyapp.domain.xp;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberXpHistory(
        UUID id,
        UUID memberId,
        int year,
        int month, // 1-12
        int finalXp,
        int finalLevel, // 1-10
        int totalTasksCompleted,
        OffsetDateTime createdAt
) {
}

