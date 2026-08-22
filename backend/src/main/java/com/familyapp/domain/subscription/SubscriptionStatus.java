package com.familyapp.domain.subscription;

/**
 * Whether a family may use the paid features.
 *
 * Only TRIAL, ACTIVE and GRACE are entitled. The distinction between them matters
 * for what the app says, not for what it allows.
 */
public enum SubscriptionStatus {
    /** Inside the three-month server-side trial. No payment method needed. */
    TRIAL,

    /** Paying, with a store period that has not ended. */
    ACTIVE,

    /**
     * Payment failed and the store is retrying. Still entitled: taking the app away
     * from a family because a card expired is how you lose them.
     */
    GRACE,

    /** Cancelled but paid up to the end of the period; entitled until then. */
    CANCELED,

    /** Trial ran out or the paid period ended. Not entitled. */
    EXPIRED,

    /**
     * Free access granted by hand -- family, friends, beta testers. Outranks every
     * other state, including an expired trial, and is never written by a webhook.
     */
    COMPED;

    public boolean isEntitled() {
        return this != EXPIRED;
    }
}
