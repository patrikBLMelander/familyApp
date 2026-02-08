package com.familyapp.infrastructure.menstrualcycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MenstrualCycleJpaRepository extends JpaRepository<MenstrualCycleEntity, UUID> {
    
    /**
     * Find all menstrual cycle entries for a specific member, ordered by period start date (descending).
     */
    @Query("SELECT m FROM MenstrualCycleEntity m WHERE m.familyMember.id = :memberId ORDER BY m.periodStartDate DESC")
    List<MenstrualCycleEntity> findByFamilyMemberIdOrderByPeriodStartDateDesc(@Param("memberId") UUID memberId);
}
