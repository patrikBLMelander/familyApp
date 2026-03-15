package com.familyapp.domain.chore;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DailyChoreCompletion(
        UUID id,
        UUID choreId,
        UUID memberId,
        LocalDate occurrenceDate,
        OffsetDateTime completedAt
) {}
