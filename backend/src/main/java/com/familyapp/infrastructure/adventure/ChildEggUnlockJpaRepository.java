package com.familyapp.infrastructure.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChildEggUnlockJpaRepository extends JpaRepository<ChildEggUnlockEntity, UUID> {

    @Query("SELECT u.eggType FROM ChildEggUnlockEntity u WHERE u.member.id = :memberId")
    List<String> findEggTypesByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT COUNT(u) > 0 FROM ChildEggUnlockEntity u WHERE u.member.id = :memberId AND u.eggType = :eggType")
    boolean existsByMemberAndEggType(@Param("memberId") UUID memberId, @Param("eggType") String eggType);
}
