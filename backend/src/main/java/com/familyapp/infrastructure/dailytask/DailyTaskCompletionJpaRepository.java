package com.familyapp.infrastructure.dailytask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyTaskCompletionJpaRepository extends JpaRepository<DailyTaskCompletionEntity, UUID> {
    
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.task.id = :taskId AND c.completedDate = :date")
    List<DailyTaskCompletionEntity> findByTaskIdAndCompletedDate(@Param("taskId") UUID taskId, @Param("date") LocalDate date);
    
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.task.id = :taskId AND c.completedDate = :date AND c.member.id = :memberId")
    Optional<DailyTaskCompletionEntity> findByTaskIdAndCompletedDateAndMemberId(@Param("taskId") UUID taskId, @Param("date") LocalDate date, @Param("memberId") UUID memberId);
    
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.completedDate = :date")
    List<DailyTaskCompletionEntity> findByCompletedDate(@Param("date") LocalDate date);
    
}

