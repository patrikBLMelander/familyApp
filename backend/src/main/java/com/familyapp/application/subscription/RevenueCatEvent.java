package com.familyapp.application.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A RevenueCat webhook body.
 *
 * Only the fields this app acts on are mapped; unknown ones are ignored rather than
 * rejected, because RevenueCat adds fields to the payload without a version bump and
 * a strict mapping would start failing on its own one day.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueCatEvent(
        @JsonProperty("api_version") String apiVersion,
        Event event
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            /** The provider's event id. Carries the idempotency guarantee. */
            String id,
            String type,
            /** Set to the family id by Billing.identify on Android. */
            @JsonProperty("app_user_id") String appUserId,
            @JsonProperty("original_app_user_id") String originalAppUserId,
            /** Every id RevenueCat has merged into this customer, anonymous ones included. */
            List<String> aliases,
            @JsonProperty("product_id") String productId,
            @JsonProperty("period_type") String periodType,
            /** PLAY_STORE, APP_STORE, STRIPE, … */
            String store,
            /** SANDBOX or PRODUCTION. */
            String environment,
            @JsonProperty("entitlement_ids") List<String> entitlementIds,
            /** UNSUBSCRIBE, BILLING_ERROR, CUSTOMER_SUPPORT, … on a CANCELLATION. */
            @JsonProperty("cancel_reason") String cancelReason,
            @JsonProperty("expiration_at_ms") Long expirationAtMs,
            @JsonProperty("grace_period_expiration_at_ms") Long gracePeriodExpirationAtMs,
            /** Stable across renewals, unlike transaction_id. */
            @JsonProperty("original_transaction_id") String originalTransactionId,
            @JsonProperty("event_timestamp_ms") Long eventTimestampMs
    ) {
    }
}
