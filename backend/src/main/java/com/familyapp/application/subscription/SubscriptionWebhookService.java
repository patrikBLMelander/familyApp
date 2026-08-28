package com.familyapp.application.subscription;

import com.familyapp.domain.subscription.FamilySubscription;
import com.familyapp.domain.subscription.SubscriptionStatus;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.subscription.FamilySubscriptionEntity;
import com.familyapp.infrastructure.subscription.FamilySubscriptionJpaRepository;
import com.familyapp.infrastructure.subscription.SubscriptionEventEntity;
import com.familyapp.infrastructure.subscription.SubscriptionEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * The only path by which store state reaches this database.
 *
 * A client is never trusted to report its own purchase: an app saying "I paid" is a
 * request, not evidence. RevenueCat sees the receipt, and what it tells us here is
 * what gets written.
 *
 * Two rules the rest of this class exists to keep:
 *
 * Nothing is ever lost. Every event is stored before it is interpreted, so an event
 * type this code does not understand, or one naming a family that cannot be found,
 * still leaves a record. That record is the only way to answer "they say they paid in
 * March and were locked out in April" once the app store's own console has moved on.
 *
 * Nothing is applied twice. Renewals, retries after a timeout and RevenueCat's own
 * redelivery all mean the same event arrives more than once, and applying a
 * cancellation twice would take a paid month off a family.
 *
 * A comp is never touched here. Free access is granted by hand and outranks the store,
 * so no webhook may revoke it -- see SubscriptionStatus.COMPED.
 */
@Service
public class SubscriptionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionWebhookService.class);

    /** What happened, so the controller can answer and the logs can say why. */
    public enum Outcome {
        /** Stored and the family's subscription updated. */
        APPLIED,
        /** Seen before. Nothing written. */
        DUPLICATE,
        /** Stored, but no family matched the ids in it. Needs a human. */
        UNRESOLVED,
        /** Stored; this type carries no state change (TEST, or one we do not act on). */
        RECORDED
    }

    private final SubscriptionEventJpaRepository eventRepository;
    private final FamilySubscriptionJpaRepository subscriptionRepository;
    private final FamilyJpaRepository familyRepository;
    private final boolean acceptSandbox;

    public SubscriptionWebhookService(
            SubscriptionEventJpaRepository eventRepository,
            FamilySubscriptionJpaRepository subscriptionRepository,
            FamilyJpaRepository familyRepository,
            @Value("${kidquest.subscription.accept-sandbox:true}") boolean acceptSandbox
    ) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.familyRepository = familyRepository;
        this.acceptSandbox = acceptSandbox;
    }

    @Transactional
    public Outcome handle(RevenueCatEvent.Event event, String rawBody) {
        var eventId = event.id();
        if (eventId == null || eventId.isBlank()) {
            // Without an id there is no idempotency, and a renewal storm would apply
            // repeatedly. Refusing is safer than guessing an id.
            throw new IllegalArgumentException("Webhook event has no id");
        }
        if (eventRepository.existsByProviderEventId(eventId)) {
            log.info("Subscription webhook {} already processed; ignoring redelivery", eventId);
            return Outcome.DUPLICATE;
        }

        var familyId = resolveFamily(event);
        // Stored first, and deliberately in the same transaction as the update below:
        // if interpreting the event fails, both roll back and RevenueCat retries into
        // a clean slate rather than a half-applied one.
        store(event, familyId, rawBody);

        if (familyId == null) {
            log.error(
                    "Subscription webhook {} ({}) names no known family: app_user_id={}, original={}. "
                            + "Stored for inspection; the purchase is NOT entitled.",
                    eventId, event.type(), event.appUserId(), event.originalAppUserId()
            );
            return Outcome.UNRESOLVED;
        }

        if (isUnappliedSandbox(event)) {
            log.warn("Subscription webhook {} is a SANDBOX event and sandbox is not accepted; stored only", eventId);
            return Outcome.RECORDED;
        }

        return apply(familyId, event);
    }

    /**
     * app_user_id is the family id, because that is what Billing.identify sets. The
     * fallbacks matter anyway: a purchase completed before the app called logIn is
     * attributed to an anonymous RevenueCat id, and only the aliases or a previously
     * stored customer id connect it back to a family.
     */
    private UUID resolveFamily(RevenueCatEvent.Event event) {
        var candidates = new ArrayList<String>();
        candidates.add(event.appUserId());
        candidates.add(event.originalAppUserId());
        if (event.aliases() != null) {
            candidates.addAll(event.aliases());
        }

        for (var candidate : candidates) {
            var uuid = parseUuid(candidate);
            if (uuid != null && familyRepository.existsById(uuid)) {
                return uuid;
            }
        }
        for (var candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            var existing = subscriptionRepository.findByProviderCustomerId(candidate);
            if (existing.isPresent()) {
                return existing.get().getFamilyId();
            }
        }
        return null;
    }

    private Outcome apply(UUID familyId, RevenueCatEvent.Event event) {
        var entity = subscriptionRepository.findById(familyId)
                .orElseGet(() -> provisionForPurchase(familyId));
        var now = OffsetDateTime.now();
        var type = event.type() == null ? "" : event.type().toUpperCase(Locale.ROOT);

        switch (type) {
            case "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE",
                 "NON_RENEWING_PURCHASE", "SUBSCRIPTION_EXTENDED" -> {
                entity.setStatus(SubscriptionStatus.ACTIVE.name());
                entity.setCancelAtPeriodEnd(false);
                applyPeriodEnd(entity, event.expirationAtMs());
            }
            case "CANCELLATION" -> {
                if (isRefund(event)) {
                    // A refund is not a cancellation at period end: the money went
                    // back, so access goes with it.
                    entity.setStatus(SubscriptionStatus.EXPIRED.name());
                    entity.setCurrentPeriodEnd(now);
                    entity.setCancelAtPeriodEnd(false);
                    log.info("Family {} refunded ({}); entitlement ended now", familyId, event.cancelReason());
                } else {
                    // Auto-renew is off. They keep what they paid for until it runs
                    // out, which is both fair and what the stores expect.
                    entity.setCancelAtPeriodEnd(true);
                    applyPeriodEnd(entity, event.expirationAtMs());
                    entity.setStatus(SubscriptionStatus.CANCELED.name());
                }
            }
            case "BILLING_ISSUE" -> {
                // Still entitled. Taking the app away from a family because a card
                // expired is how you lose them, and the store is still retrying.
                entity.setStatus(SubscriptionStatus.GRACE.name());
                var graceUntil = event.gracePeriodExpirationAtMs() != null
                        ? event.gracePeriodExpirationAtMs()
                        : event.expirationAtMs();
                applyPeriodEnd(entity, graceUntil);
            }
            case "EXPIRATION" -> {
                entity.setStatus(SubscriptionStatus.EXPIRED.name());
                applyPeriodEnd(entity, event.expirationAtMs());
            }
            case "SUBSCRIPTION_PAUSED" -> {
                // Play's pause takes effect at the end of the paid period, so this is
                // a cancellation in every way that matters here -- not an immediate
                // lockout, which would cut a family off mid-month.
                entity.setCancelAtPeriodEnd(true);
                applyPeriodEnd(entity, event.expirationAtMs());
                entity.setStatus(SubscriptionStatus.CANCELED.name());
            }
            case "TRANSFER" -> {
                // A purchase moving between RevenueCat customers. Which family should
                // gain and which should lose it is not derivable from this payload
                // alone, and guessing wrong either sells access twice or takes it from
                // someone who paid. Recorded for a human.
                log.error(
                        "Subscription TRANSFER event {} for family {} needs manual review; nothing changed",
                        event.id(), familyId
                );
                return Outcome.RECORDED;
            }
            case "TEST" -> {
                log.info("Subscription webhook test event received for family {}", familyId);
                return Outcome.RECORDED;
            }
            default -> {
                log.warn("Unhandled subscription event type '{}' ({}); stored, nothing changed", type, event.id());
                return Outcome.RECORDED;
            }
        }

        // The status written above is not the authority -- SubscriptionService
        // recomputes it from the dates on every read -- but it is what a query like
        // "how many families are cancelling" reads, so it is kept honest here rather
        // than left to drift until the family's next status call.
        //
        // Store metadata, on every applied event, so the row always reflects the most
        // recent purchase rather than the first one.
        entity.setPlatform(platformFor(event.store()));
        if (event.productId() != null) {
            entity.setStoreProductId(event.productId());
        }
        if (event.originalTransactionId() != null) {
            entity.setStoreTransactionId(event.originalTransactionId());
        }
        if (event.appUserId() != null) {
            entity.setProviderCustomerId(event.appUserId());
        }
        entity.setUpdatedAt(now);
        subscriptionRepository.save(entity);

        log.info("Family {} subscription now {} from {} (period ends {})",
                familyId, entity.getStatus(), type, entity.getCurrentPeriodEnd());
        return Outcome.APPLIED;
    }

    /**
     * A family that pays before ever reading their status has no row yet. The trial
     * dates are required by the schema, so they are filled in as if the trial had run
     * -- the paid period is what will decide entitlement, and back-dating avoids
     * handing a paying family a fresh three months free on top.
     */
    private FamilySubscriptionEntity provisionForPurchase(UUID familyId) {
        var now = OffsetDateTime.now();
        var entity = new FamilySubscriptionEntity();
        entity.setFamilyId(familyId);
        entity.setStatus(SubscriptionStatus.TRIAL.name());
        entity.setTrialStartedAt(now.minusMonths(FamilySubscription.TRIAL_MONTHS));
        entity.setTrialEndsAt(now);
        entity.setCancelAtPeriodEnd(false);
        entity.setComped(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        log.info("Provisioning subscription row for family {} from a purchase", familyId);
        return entity;
    }

    private void applyPeriodEnd(FamilySubscriptionEntity entity, Long epochMillis) {
        if (epochMillis == null) {
            return;
        }
        var incoming = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
        var current = entity.getCurrentPeriodEnd();
        // Events can arrive out of order -- a renewal after the expiry it superseded.
        // Only ever move the period end forward, so a late straggler cannot shorten a
        // period that has already been extended.
        if (current == null || incoming.isAfter(current)) {
            entity.setCurrentPeriodEnd(incoming);
        }
    }

    private boolean isRefund(RevenueCatEvent.Event event) {
        return "CUSTOMER_SUPPORT".equalsIgnoreCase(event.cancelReason());
    }

    private boolean isUnappliedSandbox(RevenueCatEvent.Event event) {
        return !acceptSandbox && "SANDBOX".equalsIgnoreCase(event.environment());
    }

    private String platformFor(String store) {
        if (store == null) {
            return null;
        }
        return switch (store.toUpperCase(Locale.ROOT)) {
            case "PLAY_STORE" -> "ANDROID";
            case "APP_STORE", "MAC_APP_STORE" -> "IOS";
            case "STRIPE", "PADDLE", "RC_BILLING" -> "WEB";
            default -> null;
        };
    }

    private void store(RevenueCatEvent.Event event, UUID familyId, String rawBody) {
        var stored = new SubscriptionEventEntity();
        stored.setId(UUID.randomUUID());
        stored.setFamilyId(familyId);
        stored.setProviderEventId(event.id());
        stored.setEventType(truncate(event.type() == null ? "UNKNOWN" : event.type(), 50));
        stored.setPayload(rawBody);
        stored.setReceivedAt(OffsetDateTime.now());
        eventRepository.save(stored);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** Exposed for the controller's startup warning. */
    public boolean acceptsSandbox() {
        return acceptSandbox;
    }

}
