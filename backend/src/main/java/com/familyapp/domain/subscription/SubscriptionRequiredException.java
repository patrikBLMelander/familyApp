package com.familyapp.domain.subscription;

/**
 * A family's entitlement has run out and they tried to do something that needs it.
 *
 * Answered as 402 Payment Required, which is the one status code that says precisely
 * this. It is deliberately distinct from 403: the caller is who they say they are and
 * is allowed to do this in principle -- there is simply an unpaid bill in the way, and
 * a client should route to a paywall rather than to an error.
 */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
