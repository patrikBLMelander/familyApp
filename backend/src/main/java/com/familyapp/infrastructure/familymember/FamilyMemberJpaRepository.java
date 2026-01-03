package com.familyapp.infrastructure.familymember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberJpaRepository extends JpaRepository<FamilyMemberEntity, UUID> {
    Optional<FamilyMemberEntity> findByDeviceToken(String deviceToken);
    Optional<FamilyMemberEntity> findByEmail(String email);
    List<FamilyMemberEntity> findByRole(String role);
}

