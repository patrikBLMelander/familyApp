package com.familyapp.infrastructure.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavingsGoalJpaRepository extends JpaRepository<SavingsGoalEntity, UUID> {
    List<SavingsGoalEntity> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
    List<SavingsGoalEntity> findByMemberIdAndIsActiveTrueAndIsCompletedFalseOrderByCreatedAtDesc(UUID memberId);
}
