package com.familyapp.application.affiliate;

import com.familyapp.application.subscription.RevenueCatEvent;
import com.familyapp.domain.affiliate.AffiliateStatus;
import com.familyapp.domain.affiliate.CommissionStatus;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionEntity;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateEntity;
import com.familyapp.infrastructure.affiliate.AffiliateJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateReferralEntity;
import com.familyapp.infrastructure.affiliate.AffiliateReferralJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
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
 * The money math and the guards around it. Commission is 20% of the NET the developer keeps
 * after store fees, once per paid period, capped, idempotent, and reversible on refund.
 */
class AffiliateCommissionServiceTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final String EVENT_ID = "evt_paid_1";

    private AffiliateReferralJpaRepository referrals;
    private AffiliateCommissionJpaRepository commissions;
    private AffiliateJpaRepository affiliates;
    private AffiliateCommissionService service;

    @BeforeEach
    void setUp() {
        referrals = mock(AffiliateReferralJpaRepository.class);
        commissions = mock(AffiliateCommissionJpaRepository.class);
        affiliates = mock(AffiliateJpaRepository.class);
        service = new AffiliateCommissionService(referrals, commissions, affiliates, new BigDecimal("0.85"));
        // Default happy wiring: family attributed to an active affiliate with 20% / 12-cap.
        when(referrals.findByFamilyId(FAMILY)).thenReturn(Optional.of(referral()));
        when(affiliates.findById(AFFILIATE)).thenReturn(Optional.of(affiliate(AffiliateStatus.ACTIVE, 20, 12)));
        when(commissions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AffiliateReferralEntity referral() {
        var r = new AffiliateReferralEntity();
        r.setId(UUID.randomUUID());
        r.setAffiliateId(AFFILIATE);
        r.setFamilyId(FAMILY);
        r.setSource("CODE");
        r.setAttributedAt(OffsetDateTime.now());
        return r;
    }

    private AffiliateEntity affiliate(AffiliateStatus status, int pct, int cap) {
        var a = new AffiliateEntity();
        a.setId(AFFILIATE);
        a.setName("Anna");
        a.setEmail("anna@example.com");
        a.setReferralCode("ANNA2026");
        a.setCommissionPct(new BigDecimal(pct));
        a.setCommissionMonthCap(cap);
        a.setStatus(status.name());
        return a;
    }

    private RevenueCatEvent.Event event(String periodType, Double price) {
        return new RevenueCatEvent.Event(
                EVENT_ID, "RENEWAL", FAMILY.toString(), FAMILY.toString(), List.of(),
                "kidquest_monthly", periodType, "PLAY_STORE", "PRODUCTION", List.of("pro"),
                null, null, null, "GPA.1", System.currentTimeMillis(),
                price, "SEK", 0.85
        );
    }

    private AffiliateCommissionEntity savedCommission() {
        var captor = ArgumentCaptor.forClass(AffiliateCommissionEntity.class);
        verify(commissions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void commission_is_20_percent_of_net_after_store_fee() {
        service.recordPaidPeriod(FAMILY, event("NORMAL", 49.0));

        var c = savedCommission();
        // 49 * 0.85 (takehome) = 41.65 net; 20% = 8.33
        assertThat(c.getAmount()).isEqualByComparingTo("8.33");
        assertThat(c.getStatus()).isEqualTo(CommissionStatus.PENDING.name());
        assertThat(c.getPeriodIndex()).isEqualTo(1);
        assertThat(c.getCurrency()).isEqualTo("SEK");
    }

    @Test
    void a_trial_period_accrues_nothing() {
        service.recordPaidPeriod(FAMILY, event("TRIAL", 0.0));
        verify(commissions, never()).save(any());
    }

    @Test
    void an_unattributed_family_accrues_nothing() {
        when(referrals.findByFamilyId(FAMILY)).thenReturn(Optional.empty());
        service.recordPaidPeriod(FAMILY, event("NORMAL", 49.0));
        verify(commissions, never()).save(any());
    }

    @Test
    void the_month_cap_stops_accrual() {
        when(commissions.countByAffiliateIdAndFamilyId(AFFILIATE, FAMILY)).thenReturn(12L);
        service.recordPaidPeriod(FAMILY, event("NORMAL", 49.0));
        verify(commissions, never()).save(any());
    }

    @Test
    void a_redelivered_event_accrues_only_once() {
        when(commissions.existsBySubscriptionEventRef(EVENT_ID)).thenReturn(true);
        service.recordPaidPeriod(FAMILY, event("NORMAL", 49.0));
        verify(commissions, never()).save(any());
    }

    @Test
    void a_paused_affiliate_accrues_nothing() {
        when(affiliates.findById(AFFILIATE)).thenReturn(Optional.of(affiliate(AffiliateStatus.PAUSED, 20, 12)));
        service.recordPaidPeriod(FAMILY, event("NORMAL", 49.0));
        verify(commissions, never()).save(any());
    }

    @Test
    void a_refund_claws_back_the_latest_reversible_commission() {
        var existing = new AffiliateCommissionEntity();
        existing.setId(UUID.randomUUID());
        existing.setStatus(CommissionStatus.PENDING.name());
        existing.setAmount(new BigDecimal("8.33"));
        existing.setCurrency("SEK");
        when(commissions.findFirstByFamilyIdAndStatusInOrderByEarnedAtDesc(any(), any()))
                .thenReturn(Optional.of(existing));

        service.clawback(FAMILY, "CUSTOMER_SUPPORT");

        assertThat(existing.getStatus()).isEqualTo(CommissionStatus.CLAWED_BACK.name());
        verify(commissions).save(existing);
    }
}
