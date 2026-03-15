package com.familyapp.infrastructure.chore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyChoreCompletionJpaRepository extends JpaRepository<DailyChoreCompletionEntity, UUID> {

    Optional<DailyChoreCompletionEntity> findByChore_IdAndMember_IdAndOccurrenceDate(
            UUID choreId, UUID memberId, LocalDate occurrenceDate
    );

    @Query("SELECT c FROM DailyChoreCompletionEntity c WHERE c.chore.id IN :choreIds AND c.member.id = :memberId AND c.occurrenceDate = :date")
    List<DailyChoreCompletionEntity> findByChoreIdsAndMemberIdAndDate(
            @Param("choreIds") List<UUID> choreIds,
            @Param("memberId") UUID memberId,
            @Param("date") LocalDate date
    );
}
