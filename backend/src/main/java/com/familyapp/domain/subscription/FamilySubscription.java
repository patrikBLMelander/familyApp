package com.familyapp.domain.subscription;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A family's subscription state. Entitlement is per family, not per member: one
 * parent subscribing covers the household, including children whose device tokens
 * have no store account.
 */
public record FamilySubscription(
        UUID familyId,
        SubscriptionStatus status,
        OffsetDateTime trialStartedAt,
        OffsetDateTime trialEndsAt,
        OffsetDateTime currentPeriodEnd,
        String platform,
        String storeProductId,
        String storeTransactionId,
        String providerCustomerId,
        boolean cancelAtPeriodEnd,
        boolean comped,
        OffsetDateTime compExpiresAt,
        String compReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static final int TRIAL_MONTHS = 3;
}
