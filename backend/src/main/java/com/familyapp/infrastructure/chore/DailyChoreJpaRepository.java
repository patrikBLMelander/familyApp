package com.familyapp.infrastructure.chore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DailyChoreJpaRepository extends JpaRepository<DailyChoreEntity, UUID> {

    @Query("SELECT c FROM DailyChoreEntity c WHERE c.member.id = :memberId AND c.isActive = true")
    List<DailyChoreEntity> findByMemberIdAndIsActiveTrue(@Param("memberId") UUID memberId);

    @Query("SELECT c FROM DailyChoreEntity c WHERE c.family.id = :familyId AND c.member.id = :memberId AND c.isActive = true")
    List<DailyChoreEntity> findByFamilyIdAndMemberIdAndIsActiveTrue(
            @Param("familyId") UUID familyId,
            @Param("memberId") UUID memberId
    );
}
