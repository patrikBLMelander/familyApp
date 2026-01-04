package com.familyapp.infrastructure.xp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberXpProgressJpaRepository extends JpaRepository<MemberXpProgressEntity, UUID> {
    Optional<MemberXpProgressEntity> findByMemberIdAndYearAndMonth(UUID memberId, int year, int month);
}

