package com.familyapp.domain.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CalendarEvent(
        UUID id,
        UUID familyId,
        UUID categoryId,
        String title,
        String description,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        boolean isAllDay,
        String location,
        UUID createdById,
        RecurringType recurringType,
        Integer recurringInterval,
        LocalDate recurringEndDate,
        Integer recurringEndCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Set<UUID> participantIds
) {
    public enum RecurringType {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
    }

    public boolean isRecurring() {
        return recurringType != null;
    }

    public boolean hasEndDateTime() {
        return endDateTime != null;
    }
}

