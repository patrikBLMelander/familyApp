package com.familyapp.infrastructure.dailytask;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Same lookup as above but with a row-level write lock (SELECT … FOR UPDATE).
     * Used during toggle to serialize concurrent requests for the same task/member/date,
     * preventing double-completion or double-XP removal.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.task.id = :taskId AND c.completedDate = :date AND c.member.id = :memberId")
    Optional<DailyTaskCompletionEntity> findByTaskIdAndCompletedDateAndMemberIdForUpdate(@Param("taskId") UUID taskId, @Param("date") LocalDate date, @Param("memberId") UUID memberId);
    
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.completedDate = :date")
    List<DailyTaskCompletionEntity> findByCompletedDate(@Param("date") LocalDate date);
    
    /**
     * Find all completions for a specific date and family.
     * Optimized query to avoid fetching all completions and filtering in memory.
     */
    @Query("SELECT c FROM DailyTaskCompletionEntity c WHERE c.completedDate = :date AND c.member.family.id = :familyId")
    List<DailyTaskCompletionEntity> findByCompletedDateAndFamilyId(@Param("date") LocalDate date, @Param("familyId") UUID familyId);
    
}

