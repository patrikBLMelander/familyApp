package com.familyapp.infrastructure.dailytask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DailyTaskJpaRepository extends JpaRepository<DailyTaskEntity, UUID> {
    
    @Query("SELECT DISTINCT t FROM DailyTaskEntity t " +
           "WHERE (:day = 'MONDAY' AND t.monday = true) OR " +
           "(:day = 'TUESDAY' AND t.tuesday = true) OR " +
           "(:day = 'WEDNESDAY' AND t.wednesday = true) OR " +
           "(:day = 'THURSDAY' AND t.thursday = true) OR " +
           "(:day = 'FRIDAY' AND t.friday = true) OR " +
           "(:day = 'SATURDAY' AND t.saturday = true) OR " +
           "(:day = 'SUNDAY' AND t.sunday = true) " +
           "ORDER BY t.position ASC")
    List<DailyTaskEntity> findByDayOfWeek(@Param("day") String day);
    
    List<DailyTaskEntity> findAllByOrderByPositionAsc();
}

