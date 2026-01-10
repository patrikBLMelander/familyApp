package com.familyapp.infrastructure.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChildPetJpaRepository extends JpaRepository<ChildPetEntity, UUID> {
    Optional<ChildPetEntity> findByMemberIdAndYearAndMonth(UUID memberId, int year, int month);
}

