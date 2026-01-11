package com.familyapp.domain.calendar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarEventTaskCompletion(
        UUID id,
        UUID eventId,
        UUID memberId,  // Vem som markerade klart (för historik/spårning)
        LocalDate occurrenceDate,  // För recurring events: vilken occurrence (datum)
        OffsetDateTime completedAt
) {
}

