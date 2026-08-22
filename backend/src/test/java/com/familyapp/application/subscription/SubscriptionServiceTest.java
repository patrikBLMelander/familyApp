package com.familyapp.application.subscription;

import com.familyapp.domain.subscription.SubscriptionStatus;
import com.familyapp.infrastructure.subscription.FamilySubscriptionEntity;
import com.familyapp.infrastructure.subscription.FamilySubscriptionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Entitlement resolution decides who pays and who gets in free, so the rules are
 * pinned here rather than left to be re-derived from the service.
 */
class SubscriptionServiceTest {

    private static final UUID FAMILY = UUID.randomUUID();

    private FamilySubscriptionJpaRepository repository;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        repository = mock(FamilySubscriptionJpaRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new SubscriptionService(repository);
    }

    private FamilySubscriptionEntity row() {
        var now = OffsetDateTime.now();
        var e = new FamilySubscriptionEntity();
        e.setFamilyId(FAMILY);
        e.setStatus(SubscriptionStatus.TRIAL.name());
        e.setTrialStartedAt(now);
        e.setTrialEndsAt(now.plusMonths(3));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private void given(FamilySubscriptionEntity e) {
        when(repository.findById(FAMILY)).thenReturn(Optional.of(e));
    }

    @Test
    void provisions_a_three_month_trial_for_a_family_with_no_row() {
        when(repository.findById(FAMILY)).thenReturn(Optional.empty());

        var result = service.getOrCreate(FAMILY);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(result.trialEndsAt()).isAfter(OffsetDateTime.now().plusDays(89));
        assertThat(service.isEntitled(FAMILY)).isTrue();
    }

    @Test
    void an_unexpired_trial_is_entitled() {
        given(row());
        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(service.isEntitled(FAMILY)).isTrue();
    }

    @Test
    void an_expired_trial_with_nothing_else_is_not_entitled() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusDays(1));
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(service.isEntitled(FAMILY)).isFalse();
    }

    @Test
    void a_paid_period_outranks_the_trial() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusDays(1));
        e.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(20));
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void cancelling_keeps_access_until_the_period_ends() {
        var e = row();
        e.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(5));
        e.setCancelAtPeriodEnd(true);
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(service.isEntitled(FAMILY)).isTrue();
    }

    @Test
    void a_billing_retry_keeps_access_while_the_store_retries() {
        var e = row();
        e.setStatus(SubscriptionStatus.GRACE.name());
        e.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(3));
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.GRACE);
        assertThat(service.isEntitled(FAMILY)).isTrue();
    }

    @Test
    void a_comp_with_no_expiry_never_runs_out() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusYears(1));
        e.setComped(true);
        e.setCompExpiresAt(null);
        e.setCompReason("familj");
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.COMPED);
        assertThat(service.isEntitled(FAMILY)).isTrue();
    }

    @Test
    void a_comp_with_a_future_expiry_is_entitled() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusDays(1));
        e.setComped(true);
        e.setCompExpiresAt(OffsetDateTime.now().plusMonths(12));
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.COMPED);
    }

    @Test
    void an_expired_comp_stops_entitling() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusDays(1));
        e.setComped(true);
        e.setCompExpiresAt(OffsetDateTime.now().minusDays(1));
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(service.isEntitled(FAMILY)).isFalse();
    }

    @Test
    void a_comp_outranks_an_expired_paid_period() {
        var e = row();
        e.setTrialEndsAt(OffsetDateTime.now().minusYears(1));
        e.setCurrentPeriodEnd(OffsetDateTime.now().minusMonths(2));
        e.setComped(true);
        given(e);

        assertThat(service.getOrCreate(FAMILY).status()).isEqualTo(SubscriptionStatus.COMPED);
    }

    @Test
    void only_expired_is_locked_out() {
        for (var status : SubscriptionStatus.values()) {
            assertThat(status.isEntitled())
                    .as("%s entitled", status)
                    .isEqualTo(status != SubscriptionStatus.EXPIRED);
        }
    }
}
