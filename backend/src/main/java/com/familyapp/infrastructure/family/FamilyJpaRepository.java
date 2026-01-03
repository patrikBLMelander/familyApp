package com.familyapp.infrastructure.family;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyJpaRepository extends JpaRepository<FamilyEntity, UUID> {
}

