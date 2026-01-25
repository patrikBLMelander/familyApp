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
        boolean isTask,  // TRUE = "Dagens Att Göra", FALSE = vanlig event
        Integer xpPoints,  // NULL eller 0 om isTask = FALSE, annars XP-poäng
        boolean isRequired,  // TRUE = obligatorisk, FALSE = extra (only used when isTask = TRUE)
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

    public enum OccurrenceScope {
        THIS,              // Only this occurrence
        THIS_AND_FOLLOWING, // This and all future occurrences
        ALL                // All occurrences in the series
    }

    public boolean isRecurring() {
        return recurringType != null;
    }

    public boolean hasEndDateTime() {
        return endDateTime != null;
    }
}

