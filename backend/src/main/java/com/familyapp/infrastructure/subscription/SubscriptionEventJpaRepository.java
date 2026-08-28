package com.familyapp.infrastructure.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SubscriptionEventJpaRepository extends JpaRepository<SubscriptionEventEntity, UUID> {

    /** The idempotency check. A redelivered event is recognised here, not applied twice. */
    boolean existsByProviderEventId(String providerEventId);
}
