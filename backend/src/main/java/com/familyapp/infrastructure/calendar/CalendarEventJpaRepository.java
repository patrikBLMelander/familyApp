package com.familyapp.infrastructure.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CalendarEventJpaRepository extends JpaRepository<CalendarEventEntity, UUID> {

    @Query("SELECT e FROM CalendarEventEntity e WHERE e.family.id = :familyId " +
           "AND e.startDateTime >= :startDate AND e.startDateTime < :endDate " +
           "ORDER BY e.startDateTime ASC")
    List<CalendarEventEntity> findByFamilyIdAndDateRange(
            @Param("familyId") UUID familyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    List<CalendarEventEntity> findByFamilyIdOrderByStartDateTimeAsc(UUID familyId);
}

