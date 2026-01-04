package com.familyapp.infrastructure.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalendarEventCategoryJpaRepository extends JpaRepository<CalendarEventCategoryEntity, UUID> {
    List<CalendarEventCategoryEntity> findByFamilyIdOrderByNameAsc(UUID familyId);
}

