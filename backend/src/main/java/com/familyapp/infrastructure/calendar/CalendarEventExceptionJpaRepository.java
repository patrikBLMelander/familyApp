package com.familyapp.infrastructure.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventExceptionJpaRepository extends JpaRepository<CalendarEventExceptionEntity, UUID> {

    @Query("SELECT e FROM CalendarEventExceptionEntity e WHERE e.event.id = :eventId")
    List<CalendarEventExceptionEntity> findByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT e FROM CalendarEventExceptionEntity e WHERE e.event.id = :eventId AND e.occurrenceDate = :occurrenceDate")
    Optional<CalendarEventExceptionEntity> findByEventIdAndOccurrenceDate(
            @Param("eventId") UUID eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate
    );

    @Query("SELECT e.occurrenceDate FROM CalendarEventExceptionEntity e WHERE e.event.id = :eventId")
    List<LocalDate> findExcludedOccurrenceDates(@Param("eventId") UUID eventId);
    
    /**
     * Batch fetch all exceptions for multiple event IDs.
     * This is more efficient than calling findByEventId for each event individually.
     */
    @Query("SELECT e FROM CalendarEventExceptionEntity e WHERE e.event.id IN :eventIds")
    List<CalendarEventExceptionEntity> findByEventIds(@Param("eventIds") List<UUID> eventIds);
    
    /**
     * Batch fetch excluded occurrence dates for multiple event IDs.
     * Returns a list of objects containing eventId and occurrenceDate.
     */
    @Query("SELECT e.event.id, e.occurrenceDate FROM CalendarEventExceptionEntity e WHERE e.event.id IN :eventIds")
    List<Object[]> findExcludedOccurrenceDatesForEvents(@Param("eventIds") List<UUID> eventIds);
}
