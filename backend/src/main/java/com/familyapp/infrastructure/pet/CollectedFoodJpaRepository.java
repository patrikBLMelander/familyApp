package com.familyapp.infrastructure.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CollectedFoodJpaRepository extends JpaRepository<CollectedFoodEntity, UUID> {

    /**
     * Find all unfed food for a member
     */
    @Query("SELECT f FROM CollectedFoodEntity f WHERE f.member.id = :memberId AND f.isFed = false ORDER BY f.collectedAt ASC")
    List<CollectedFoodEntity> findUnfedFoodByMemberId(@Param("memberId") UUID memberId);

    /**
     * Find all food (fed and unfed) for a member
     */
    @Query("SELECT f FROM CollectedFoodEntity f WHERE f.member.id = :memberId ORDER BY f.collectedAt ASC")
    List<CollectedFoodEntity> findAllByMemberId(@Param("memberId") UUID memberId);

    /**
     * Find unfed food for a specific event (for validation when uncompleting)
     * Note: event_id can be null for bonus food, but this query only finds food for specific events
     */
    @Query("SELECT f FROM CollectedFoodEntity f WHERE f.member.id = :memberId AND f.event.id = :eventId AND f.isFed = false")
    List<CollectedFoodEntity> findUnfedFoodByMemberIdAndEventId(@Param("memberId") UUID memberId, @Param("eventId") UUID eventId);

    /**
     * Count total unfed food XP for a member
     */
    @Query("SELECT COALESCE(SUM(f.xpAmount), 0) FROM CollectedFoodEntity f WHERE f.member.id = :memberId AND f.isFed = false")
    int countUnfedFoodXpByMemberId(@Param("memberId") UUID memberId);

    /**
     * Get the most recent fedAt date for a member (when pet was last fed)
     * Returns null if pet has never been fed
     */
    @Query("SELECT MAX(f.fedAt) FROM CollectedFoodEntity f WHERE f.member.id = :memberId AND f.isFed = true AND f.fedAt IS NOT NULL")
    java.time.OffsetDateTime findLastFedAtByMemberId(@Param("memberId") UUID memberId);

    // Note: markFoodAsFed is handled in service layer for better control
}
