package com.familyapp.infrastructure.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventTaskCompletionJpaRepository extends JpaRepository<CalendarEventTaskCompletionEntity, UUID> {

    Optional<CalendarEventTaskCompletionEntity> findByEventIdAndMemberIdAndOccurrenceDate(
            UUID eventId,
            UUID memberId,
            LocalDate occurrenceDate
    );

    List<CalendarEventTaskCompletionEntity> findByEventId(UUID eventId);

    List<CalendarEventTaskCompletionEntity> findByMemberId(UUID memberId);

    @Query("SELECT c FROM CalendarEventTaskCompletionEntity c WHERE c.event.id = :eventId AND c.occurrenceDate = :occurrenceDate")
    List<CalendarEventTaskCompletionEntity> findByEventIdAndOccurrenceDate(
            @Param("eventId") UUID eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate
    );
}

