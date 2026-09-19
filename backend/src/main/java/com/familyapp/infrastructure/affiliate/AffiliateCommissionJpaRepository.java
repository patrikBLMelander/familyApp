package com.familyapp.infrastructure.affiliate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AffiliateCommissionJpaRepository extends JpaRepository<AffiliateCommissionEntity, UUID> {

    boolean existsBySubscriptionEventRef(String subscriptionEventRef);

    /** Periods already accrued for this family, to derive the next period_index and enforce the cap. */
    long countByAffiliateIdAndFamilyId(UUID affiliateId, UUID familyId);

    /** Most recent still-reversible commission for a family, for refund clawback. */
    Optional<AffiliateCommissionEntity> findFirstByFamilyIdAndStatusInOrderByEarnedAtDesc(
            UUID familyId, List<String> statuses);

    List<AffiliateCommissionEntity> findByAffiliateId(UUID affiliateId);

    /** Summed amount for one affiliate in one status (0 when none). */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM AffiliateCommissionEntity c "
            + "WHERE c.affiliateId = ?1 AND c.status = ?2")
    BigDecimal sumAmountByAffiliateIdAndStatus(UUID affiliateId, String status);
}
