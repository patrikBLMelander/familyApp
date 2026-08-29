package com.familyapp.infrastructure.xp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberXpHistoryJpaRepository extends JpaRepository<MemberXpHistoryEntity, UUID> {
    @Query("SELECT h FROM MemberXpHistoryEntity h WHERE h.member.id = :memberId ORDER BY h.year DESC, h.month DESC")
    List<MemberXpHistoryEntity> findByMemberIdOrderByYearDescMonthDesc(@Param("memberId") UUID memberId);

    /** Nivån för en bestämd månad. Används av den automatiska pengen. */
    @Query("SELECT h FROM MemberXpHistoryEntity h WHERE h.member.id = :memberId "
            + "AND h.year = :year AND h.month = :month")
    Optional<MemberXpHistoryEntity> findForMonth(
            @Param("memberId") UUID memberId, @Param("year") int year, @Param("month") int month);
}

