package com.familyapp.infrastructure.affiliate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AffiliateJpaRepository extends JpaRepository<AffiliateEntity, UUID> {

    Optional<AffiliateEntity> findByEmailIgnoreCase(String email);

    Optional<AffiliateEntity> findByReferralCodeIgnoreCase(String referralCode);

    Optional<AffiliateEntity> findBySessionTokenHash(String sessionTokenHash);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByReferralCodeIgnoreCase(String referralCode);
}
