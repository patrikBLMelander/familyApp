package com.familyapp.infrastructure.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdventureJpaRepository extends JpaRepository<AdventureEntity, UUID> {

    @Query("SELECT COUNT(a) FROM AdventureEntity a WHERE a.member.id = :memberId")
    long countByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT COUNT(a) FROM AdventureEntity a WHERE a.member.id = :memberId AND a.status = :status")
    long countByMemberIdAndStatus(@Param("memberId") UUID memberId, @Param("status") String status);

    @Query("SELECT a FROM AdventureEntity a WHERE a.member.id = :memberId ORDER BY a.startedAt DESC")
    List<AdventureEntity> findByMemberIdOrderByStartedAtDesc(@Param("memberId") UUID memberId);
}
