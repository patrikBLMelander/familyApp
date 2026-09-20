package com.familyapp.infrastructure.affiliate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AffiliatePayoutJpaRepository extends JpaRepository<AffiliatePayoutEntity, UUID> {

    List<AffiliatePayoutEntity> findByAffiliateIdOrderByCreatedAtDesc(UUID affiliateId);
}
