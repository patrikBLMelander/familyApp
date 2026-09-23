package com.familyapp.application.affiliate;

import com.familyapp.domain.affiliate.AffiliateStatus;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateEntity;
import com.familyapp.infrastructure.affiliate.AffiliateJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateReferralEntity;
import com.familyapp.infrastructure.affiliate.AffiliateReferralJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliateServiceTest {

    private static final UUID FAMILY = UUID.randomUUID();

    private AffiliateJpaRepository affiliates;
    private AffiliateReferralJpaRepository referrals;
    private AffiliateCommissionJpaRepository commissions;
    private com.familyapp.infrastructure.affiliate.AffiliatePayoutJpaRepository payouts;
    private PasswordEncoder encoder;
    private com.familyapp.application.subscription.SubscriptionService subscriptionService;
    private AffiliateService service;

    @BeforeEach
    void setUp() {
        affiliates = mock(AffiliateJpaRepository.class);
        referrals = mock(AffiliateReferralJpaRepository.class);
        commissions = mock(AffiliateCommissionJpaRepository.class);
        payouts = mock(com.familyapp.infrastructure.affiliate.AffiliatePayoutJpaRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(encoder.matches(any(), any()))
                .thenAnswer(i -> ("hash:" + i.getArgument(0)).equals(i.getArgument(1)));
        when(affiliates.save(any())).thenAnswer(i -> i.getArgument(0));
        var subscriptions = mock(com.familyapp.infrastructure.subscription.FamilySubscriptionJpaRepository.class);
        subscriptionService = mock(com.familyapp.application.subscription.SubscriptionService.class);
        service = new AffiliateService(
                affiliates, referrals, commissions, payouts, subscriptions, subscriptionService, encoder,
                "patrik@cubeia.com", "https://www.kidquest.se/?ref=", new BigDecimal("20.00"), 12);
    }

    private AffiliateEntity invited(String email) {
        var a = new AffiliateEntity();
        a.setId(UUID.randomUUID());
        a.setName("Anna");
        a.setEmail(email);
        a.setReferralCode("ANNA2026");
        a.setCommissionPct(new BigDecimal("20.00"));
        a.setCommissionMonthCap(12);
        a.setStatus(AffiliateStatus.INVITED.name());
        return a;
    }

    // ---- admin invite --------------------------------------------------------

    @Test
    void creating_an_affiliate_makes_an_invited_row_with_a_code() {
        when(affiliates.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(affiliates.existsByReferralCodeIgnoreCase(any())).thenReturn(false);

        var row = service.createAffiliate("Anna", "anna@example.com", null);

        assertThat(row.status()).isEqualTo(AffiliateStatus.INVITED.name());
        assertThat(row.referralCode()).isNotBlank();
        verify(affiliates).save(any());
    }

    // ---- invite-only activation ---------------------------------------------

    @Test
    void an_invited_affiliate_can_activate_by_setting_a_password() {
        var row = invited("anna@example.com");
        when(affiliates.findByEmailIgnoreCase("anna@example.com")).thenReturn(Optional.of(row));

        var session = service.activate("anna@example.com", "hunter2");

        assertThat(session.token()).isNotBlank();
        assertThat(row.getPasswordHash()).isEqualTo("hash:hunter2");
        assertThat(row.getStatus()).isEqualTo(AffiliateStatus.ACTIVE.name());
        assertThat(row.getSessionTokenHash()).isNotBlank();
    }

    @Test
    void activation_fails_without_an_invite() {
        when(affiliates.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.activate("stranger@example.com", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No invite");
    }

    @Test
    void activation_fails_if_already_activated() {
        var row = invited("anna@example.com");
        row.setPasswordHash("hash:existing");
        when(affiliates.findByEmailIgnoreCase("anna@example.com")).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> service.activate("anna@example.com", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already activated");
    }

    @Test
    void login_rejects_a_wrong_password() {
        var row = invited("anna@example.com");
        row.setPasswordHash("hash:correct");
        row.setStatus(AffiliateStatus.ACTIVE.name());
        when(affiliates.findByEmailIgnoreCase("anna@example.com")).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> service.login("anna@example.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- attribution ---------------------------------------------------------

    @Test
    void redeem_attributes_a_new_family_first_touch() {
        var row = invited("anna@example.com");
        row.setStatus(AffiliateStatus.ACTIVE.name());
        when(referrals.existsByFamilyId(FAMILY)).thenReturn(false);
        when(affiliates.findByReferralCodeIgnoreCase("ANNA2026")).thenReturn(Optional.of(row));

        service.redeem(FAMILY, "anna2026");

        var captor = ArgumentCaptor.forClass(AffiliateReferralEntity.class);
        verify(referrals).save(captor.capture());
        assertThat(captor.getValue().getAffiliateId()).isEqualTo(row.getId());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(FAMILY);
        // Koden ger familjen en extra gratismånad.
        verify(subscriptionService).grantReferralTrialExtension(FAMILY);
    }

    @Test
    void redeem_is_a_no_op_when_the_family_is_already_attributed() {
        when(referrals.existsByFamilyId(FAMILY)).thenReturn(true);
        service.redeem(FAMILY, "ANNA2026");
        verify(referrals, never()).save(any());
        // Ingen dubbel gratismånad om koden redan lösts in.
        verify(subscriptionService, never()).grantReferralTrialExtension(any());
    }

    @Test
    void redeem_rejects_an_unknown_code() {
        when(referrals.existsByFamilyId(FAMILY)).thenReturn(false);
        when(affiliates.findByReferralCodeIgnoreCase(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.redeem(FAMILY, "NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown referral code");
    }

    @Test
    void admin_is_recognised_by_configured_email() {
        assertThat(service.isAdmin("patrik@cubeia.com")).isTrue();
        assertThat(service.isAdmin("PATRIK@CUBEIA.COM")).isTrue();
        assertThat(service.isAdmin("someone@else.com")).isFalse();
    }
}
