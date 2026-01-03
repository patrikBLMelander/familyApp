package com.familyapp.domain.dailytask;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

public record DailyTask(
        java.util.UUID id,
        String name,
        String description,
        Set<DayOfWeek> daysOfWeek,
        Set<java.util.UUID> memberIds, // Empty set means applies to all members
        int position,
        java.util.UUID familyId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public enum DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    }

    public boolean isActiveOnDay(LocalDate date) {
        DayOfWeek dayOfWeek = DayOfWeek.values()[date.getDayOfWeek().getValue() - 1];
        return daysOfWeek.contains(dayOfWeek);
    }

    public boolean appliesToMember(java.util.UUID memberId) {
        return memberIds.isEmpty() || memberIds.contains(memberId);
    }
}

