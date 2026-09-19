package com.familyapp.infrastructure.affiliate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AffiliateReferralJpaRepository extends JpaRepository<AffiliateReferralEntity, UUID> {

    Optional<AffiliateReferralEntity> findByFamilyId(UUID familyId);

    boolean existsByFamilyId(UUID familyId);

    long countByAffiliateId(UUID affiliateId);
}
