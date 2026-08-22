package com.familyapp.infrastructure.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilySubscriptionJpaRepository extends JpaRepository<FamilySubscriptionEntity, UUID> {

    /** Set to the family id when a purchase is made, so webhooks can find the row. */
    Optional<FamilySubscriptionEntity> findByProviderCustomerId(String providerCustomerId);
}
