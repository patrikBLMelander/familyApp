package com.familyapp.application.subscription;

import com.familyapp.domain.subscription.SubscriptionStatus;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.subscription.FamilySubscriptionEntity;
import com.familyapp.infrastructure.subscription.FamilySubscriptionJpaRepository;
import com.familyapp.infrastructure.subscription.SubscriptionEventEntity;
import com.familyapp.infrastructure.subscription.SubscriptionEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The webhook is the only way money becomes entitlement, and every case here is one
 * that costs a real family real access if it goes wrong. Pinned rather than left to be
 * re-derived: getting a cancellation or a refund backwards is not visible in testing,
 * only in a support email a month later.
 */
class SubscriptionWebhookServiceTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final String EVENT_ID = "evt_1";

    private SubscriptionEventJpaRepository events;
    private FamilySubscriptionJpaRepository subscriptions;
    private FamilyJpaRepository families;
    private SubscriptionWebhookService service;

    @BeforeEach
    void setUp() {
        events = mock(SubscriptionEventJpaRepository.class);
        subscriptions = mock(FamilySubscriptionJpaRepository.class);
        families = mock(FamilyJpaRepository.class);
        when(subscriptions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(families.existsById(FAMILY)).thenReturn(true);
        service = new SubscriptionWebhookService(events, subscriptions, families, true);
    }

    private FamilySubscriptionEntity row() {
        var now = OffsetDateTime.now();
        var e = new FamilySubscriptionEntity();
        e.setFamilyId(FAMILY);
        e.setStatus(SubscriptionStatus.TRIAL.name());
        e.setTrialStartedAt(now.minusMonths(3));
        e.setTrialEndsAt(now.minusDays(1));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        when(subscriptions.findById(FAMILY)).thenReturn(Optional.of(e));
        return e;
    }

    private RevenueCatEvent.Event event(String type, Long expiresAtMs, String cancelReason) {
        return new RevenueCatEvent.Event(
                EVENT_ID, type, FAMILY.toString(), FAMILY.toString(), List.of(),
                "kidquest_monthly", "NORMAL", "PLAY_STORE", "PRODUCTION", List.of("pro"),
                cancelReason, expiresAtMs, null, "GPA.1234", System.currentTimeMillis()
        );
    }

    private static long inDays(int days) {
        return OffsetDateTime.now().plusDays(days).toInstant().toEpochMilli();
    }

    private FamilySubscriptionEntity saved() {
        var captor = ArgumentCaptor.forClass(FamilySubscriptionEntity.class);
        verify(subscriptions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void an_initial_purchase_makes_a_family_active() {
        row();

        var outcome = service.handle(event("INITIAL_PURCHASE", inDays(30), null), "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.APPLIED);
        var result = saved();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        assertThat(result.getCurrentPeriodEnd()).isAfter(OffsetDateTime.now().plusDays(29));
        assertThat(result.getPlatform()).isEqualTo("ANDROID");
        assertThat(result.getStoreProductId()).isEqualTo("kidquest_monthly");
    }

    @Test
    void a_redelivered_event_is_recognised_and_nothing_is_written() {
        row();
        when(events.existsByProviderEventId(EVENT_ID)).thenReturn(true);

        var outcome = service.handle(event("RENEWAL", inDays(30), null), "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.DUPLICATE);
        verify(subscriptions, never()).save(any());
        verify(events, never()).save(any());
    }

    @Test
    void a_cancellation_keeps_access_until_the_period_ends() {
        row();

        service.handle(event("CANCELLATION", inDays(20), "UNSUBSCRIBE"), "{}");

        var result = saved();
        assertThat(result.isCancelAtPeriodEnd()).isTrue();
        // Deliberately still ACTIVE-shaped: they paid for this month.
        assertThat(result.getCurrentPeriodEnd()).isAfter(OffsetDateTime.now());
    }

    @Test
    void a_refund_ends_access_immediately() {
        row();

        service.handle(event("CANCELLATION", inDays(20), "CUSTOMER_SUPPORT"), "{}");

        var result = saved();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED.name());
        assertThat(result.getCurrentPeriodEnd()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(result.isCancelAtPeriodEnd()).isFalse();
    }

    @Test
    void a_billing_issue_leaves_the_family_entitled_in_grace() {
        row();

        service.handle(event("BILLING_ISSUE", inDays(7), null), "{}");

        var result = saved();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.GRACE.name());
        assertThat(SubscriptionStatus.GRACE.isEntitled()).isTrue();
    }

    @Test
    void an_expiration_ends_entitlement() {
        row();

        service.handle(event("EXPIRATION", inDays(0), null), "{}");

        assertThat(saved().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED.name());
    }

    @Test
    void a_pause_is_treated_as_a_cancellation_rather_than_an_immediate_lockout() {
        row();

        service.handle(event("SUBSCRIPTION_PAUSED", inDays(12), null), "{}");

        var result = saved();
        assertThat(result.isCancelAtPeriodEnd()).isTrue();
        assertThat(result.getStatus()).isNotEqualTo(SubscriptionStatus.EXPIRED.name());
    }

    @Test
    void an_out_of_order_event_cannot_shorten_a_period_already_extended() {
        var existing = row();
        existing.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(30));

        service.handle(event("RENEWAL", inDays(5), null), "{}");

        assertThat(saved().getCurrentPeriodEnd()).isAfter(OffsetDateTime.now().plusDays(29));
    }

    @Test
    void a_transfer_changes_nothing_and_asks_for_a_human() {
        row();

        var outcome = service.handle(event("TRANSFER", inDays(30), null), "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.RECORDED);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void an_unknown_event_type_is_stored_without_touching_entitlement() {
        row();

        var outcome = service.handle(event("SOMETHING_NEW_REVENUECAT_ADDED", inDays(30), null), "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.RECORDED);
        verify(subscriptions, never()).save(any());
        verify(events).save(any());
    }

    @Test
    void an_event_naming_no_known_family_is_stored_for_inspection() {
        when(families.existsById(any())).thenReturn(false);
        when(subscriptions.findByProviderCustomerId(any())).thenReturn(Optional.empty());

        var outcome = service.handle(event("INITIAL_PURCHASE", inDays(30), null), "{\"raw\":true}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.UNRESOLVED);
        var captor = ArgumentCaptor.forClass(SubscriptionEventEntity.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isNull();
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"raw\":true}");
        verify(subscriptions, never()).save(any());
    }

    @Test
    void a_purchase_made_before_login_is_matched_by_the_stored_customer_id() {
        var existing = row();
        when(families.existsById(any())).thenReturn(false);
        when(subscriptions.findByProviderCustomerId("$RCAnonymousID:abc"))
                .thenReturn(Optional.of(existing));

        var anonymous = new RevenueCatEvent.Event(
                EVENT_ID, "INITIAL_PURCHASE", "$RCAnonymousID:abc", "$RCAnonymousID:abc",
                List.of(), "kidquest_monthly", "NORMAL", "PLAY_STORE", "PRODUCTION",
                List.of("pro"), null, inDays(30), null, "GPA.1", System.currentTimeMillis()
        );

        var outcome = service.handle(anonymous, "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.APPLIED);
        assertThat(saved().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
    }

    @Test
    void a_comp_is_never_revoked_by_the_store() {
        var existing = row();
        existing.setComped(true);
        existing.setCompReason("betatestare");

        service.handle(event("EXPIRATION", inDays(0), null), "{}");

        var result = saved();
        assertThat(result.isComped()).isTrue();
        assertThat(result.getCompReason()).isEqualTo("betatestare");
    }

    @Test
    void an_event_without_an_id_is_refused_rather_than_applied_unguarded() {
        row();
        var idless = new RevenueCatEvent.Event(
                null, "RENEWAL", FAMILY.toString(), FAMILY.toString(), List.of(),
                "kidquest_monthly", "NORMAL", "PLAY_STORE", "PRODUCTION", List.of("pro"),
                null, inDays(30), null, "GPA.1", System.currentTimeMillis()
        );

        try {
            service.handle(idless, "{}");
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("no id");
        }
        verify(subscriptions, never()).save(any());
    }

    @Test
    void a_sandbox_event_is_stored_but_not_applied_when_sandbox_is_off() {
        service = new SubscriptionWebhookService(events, subscriptions, families, false);
        row();
        var sandbox = new RevenueCatEvent.Event(
                EVENT_ID, "INITIAL_PURCHASE", FAMILY.toString(), FAMILY.toString(), List.of(),
                "kidquest_monthly", "NORMAL", "PLAY_STORE", "SANDBOX", List.of("pro"),
                null, inDays(30), null, "GPA.1", System.currentTimeMillis()
        );

        var outcome = service.handle(sandbox, "{}");

        assertThat(outcome).isEqualTo(SubscriptionWebhookService.Outcome.RECORDED);
        verify(subscriptions, never()).save(any());
        verify(events).save(any());
    }
}
