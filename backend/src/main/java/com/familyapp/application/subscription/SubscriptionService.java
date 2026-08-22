package com.familyapp.application.subscription;

import com.familyapp.domain.subscription.FamilySubscription;
import com.familyapp.domain.subscription.SubscriptionStatus;
import com.familyapp.infrastructure.subscription.FamilySubscriptionEntity;
import com.familyapp.infrastructure.subscription.FamilySubscriptionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The single place that decides whether a family may use the paid features.
 *
 * Status is recomputed from dates on every read rather than trusted from the stored
 * column, because a trial expires with the passage of time and nothing writes a row
 * at that moment. The column is kept in step so it stays queryable, but it is never
 * the authority.
 *
 * All comparisons are on instants (OffsetDateTime), so no server timezone is
 * involved. That matters: the pet month is keyed on the JVM's local date and gets
 * the first two hours of every month wrong in Sweden. Billing must not inherit that.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final FamilySubscriptionJpaRepository repository;

    public SubscriptionService(FamilySubscriptionJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Reads a family's subscription, provisioning a trial if it has no row yet.
     *
     * The write on a read path is deliberate. A family with no row has no meaningful
     * status, and every route in would otherwise need to remember to create one:
     * registration, the V41 backfill, and any family restored from a backup. Doing it
     * here means there is exactly one rule for when a trial starts.
     */
    @Transactional
    public FamilySubscription getOrCreate(UUID familyId) {
        var entity = repository.findById(familyId).orElseGet(() -> createTrial(familyId));
        var effective = resolveStatus(entity, OffsetDateTime.now());

        // Keep the stored column honest without making it the source of truth.
        if (!effective.name().equals(entity.getStatus())) {
            log.info("Family {} subscription {} -> {}", familyId, entity.getStatus(), effective);
            entity.setStatus(effective.name());
            entity.setUpdatedAt(OffsetDateTime.now());
            entity = repository.save(entity);
        }
        return toDomain(entity, effective);
    }

    @Transactional(readOnly = true)
    public boolean isEntitled(UUID familyId) {
        return repository.findById(familyId)
                .map(e -> resolveStatus(e, OffsetDateTime.now()))
                // No row yet means the family has not been provisioned, which happens
                // only before their first status read. Treat that as inside the trial
                // rather than locking out a family that just registered.
                .orElse(SubscriptionStatus.TRIAL)
                .isEntitled();
    }

    /** Whole days until the trial ends; 0 once it has. */
    public long trialDaysRemaining(FamilySubscription subscription) {
        var now = OffsetDateTime.now();
        if (subscription.trialEndsAt().isBefore(now)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(now, subscription.trialEndsAt());
    }

    private FamilySubscriptionEntity createTrial(UUID familyId) {
        var now = OffsetDateTime.now();
        var entity = new FamilySubscriptionEntity();
        entity.setFamilyId(familyId);
        entity.setStatus(SubscriptionStatus.TRIAL.name());
        entity.setTrialStartedAt(now);
        entity.setTrialEndsAt(now.plusMonths(FamilySubscription.TRIAL_MONTHS));
        entity.setCancelAtPeriodEnd(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        log.info("Starting {}-month trial for family {}", FamilySubscription.TRIAL_MONTHS, familyId);
        return repository.save(entity);
    }

    /**
     * A paid period always wins over the trial, so a family that subscribes early is
     * not downgraded to TRIAL for the remainder of it.
     */
    private SubscriptionStatus resolveStatus(FamilySubscriptionEntity e, OffsetDateTime now) {
        // A comp outranks everything. Checked first so granting one does not depend on
        // the trial or store state, and so a webhook can never revoke it.
        if (e.isComped()
                && (e.getCompExpiresAt() == null || e.getCompExpiresAt().isAfter(now))) {
            return SubscriptionStatus.COMPED;
        }

        var periodEnd = e.getCurrentPeriodEnd();
        var stored = e.getStatus();

        if (periodEnd != null && periodEnd.isAfter(now)) {
            // GRACE is set by the webhook when the store reports a billing retry, and
            // must survive until the retry window closes.
            if (SubscriptionStatus.GRACE.name().equals(stored)) {
                return SubscriptionStatus.GRACE;
            }
            return e.isCancelAtPeriodEnd() ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE;
        }

        if (e.getTrialEndsAt() != null && e.getTrialEndsAt().isAfter(now)) {
            return SubscriptionStatus.TRIAL;
        }

        return SubscriptionStatus.EXPIRED;
    }

    private FamilySubscription toDomain(FamilySubscriptionEntity e, SubscriptionStatus effective) {
        return new FamilySubscription(
                e.getFamilyId(),
                effective,
                e.getTrialStartedAt(),
                e.getTrialEndsAt(),
                e.getCurrentPeriodEnd(),
                e.getPlatform(),
                e.getStoreProductId(),
                e.getStoreTransactionId(),
                e.getProviderCustomerId(),
                e.isCancelAtPeriodEnd(),
                e.isComped(),
                e.getCompExpiresAt(),
                e.getCompReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
