package com.familyapp.infrastructure.familymember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberJpaRepository extends JpaRepository<FamilyMemberEntity, UUID> {
    Optional<FamilyMemberEntity> findByDeviceToken(String deviceToken);
    Optional<FamilyMemberEntity> findByEmail(String email);
    List<FamilyMemberEntity> findByRole(String role);
    
    /**
     * Find all members by family ID and role.
     * Optimized query to avoid fetching all members and filtering in memory.
     */
    @Query("SELECT m FROM FamilyMemberEntity m WHERE m.family.id = :familyId AND m.role = :role")
    List<FamilyMemberEntity> findByFamilyIdAndRole(@Param("familyId") UUID familyId, @Param("role") String role);
    
    /**
     * Find all members by family ID.
     * Optimized query to avoid fetching all members and filtering in memory.
     */
    @Query("SELECT m FROM FamilyMemberEntity m WHERE m.family.id = :familyId")
    List<FamilyMemberEntity> findByFamilyId(@Param("familyId") UUID familyId);
    
    /**
     * Find all members by family ID, ordered by name.
     * Optimized query for getAllMembers.
     */
    @Query("SELECT m FROM FamilyMemberEntity m WHERE m.family.id = :familyId ORDER BY m.name ASC")
    List<FamilyMemberEntity> findByFamilyIdOrderByNameAsc(@Param("familyId") UUID familyId);
}

