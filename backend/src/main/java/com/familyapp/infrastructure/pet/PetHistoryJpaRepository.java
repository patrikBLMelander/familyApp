package com.familyapp.infrastructure.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PetHistoryJpaRepository extends JpaRepository<PetHistoryEntity, UUID> {
    @Query("SELECT h FROM PetHistoryEntity h WHERE h.member.id = :memberId ORDER BY h.year DESC, h.month DESC")
    List<PetHistoryEntity> findByMemberIdOrderByYearDescMonthDesc(@Param("memberId") UUID memberId);
}

