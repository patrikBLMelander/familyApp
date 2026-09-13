package com.familyapp.infrastructure.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdventureTicketGrantJpaRepository extends JpaRepository<AdventureTicketGrantEntity, UUID> {

    @Query("SELECT COUNT(g) FROM AdventureTicketGrantEntity g WHERE g.member.id = :memberId")
    long countByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT COUNT(g) > 0 FROM AdventureTicketGrantEntity g WHERE g.member.id = :memberId AND g.grantedFor = :grantedFor")
    boolean existsByMemberAndGrantedFor(@Param("memberId") UUID memberId, @Param("grantedFor") String grantedFor);
}
