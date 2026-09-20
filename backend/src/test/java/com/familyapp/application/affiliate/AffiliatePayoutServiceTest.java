package com.familyapp.application.affiliate;

import com.familyapp.domain.affiliate.CommissionStatus;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionEntity;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliatePayoutEntity;
import com.familyapp.infrastructure.affiliate.AffiliatePayoutJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliatePayoutServiceTest {

    private static final UUID AFFILIATE = UUID.randomUUID();

    private AffiliateCommissionJpaRepository commissions;
    private AffiliatePayoutJpaRepository payouts;
    private AffiliatePayoutService service;

    @BeforeEach
    void setUp() {
        commissions = mock(AffiliateCommissionJpaRepository.class);
        payouts = mock(AffiliatePayoutJpaRepository.class);
        service = new AffiliatePayoutService(commissions, payouts, 30);
        when(commissions.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(payouts.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AffiliateCommissionEntity commission(String status, BigDecimal amount) {
        var c = new AffiliateCommissionEntity();
        c.setId(UUID.randomUUID());
        c.setAffiliateId(AFFILIATE);
        c.setFamilyId(UUID.randomUUID());
        c.setStatus(status);
        c.setAmount(amount);
        c.setCurrency("SEK");
        c.setEarnedAt(OffsetDateTime.now());
        return c;
    }

    @Test
    void due_pending_commissions_are_approved() {
        var a = commission(CommissionStatus.PENDING.name(), new BigDecimal("8.33"));
        var b = commission(CommissionStatus.PENDING.name(), new BigDecimal("8.33"));
        when(commissions.findByStatusAndEarnedAtBefore(eq(CommissionStatus.PENDING.name()), any()))
                .thenReturn(List.of(a, b));

        var count = service.approveDueCommissions();

        assertThat(count).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(CommissionStatus.APPROVED.name());
        assertThat(b.getStatus()).isEqualTo(CommissionStatus.APPROVED.name());
    }

    @Test
    void payout_marks_approved_commissions_paid_and_records_the_total() {
        var a = commission(CommissionStatus.APPROVED.name(), new BigDecimal("8.33"));
        var b = commission(CommissionStatus.APPROVED.name(), new BigDecimal("8.33"));
        when(commissions.findByAffiliateIdAndStatus(AFFILIATE, CommissionStatus.APPROVED.name()))
                .thenReturn(List.of(a, b));

        var result = service.payOut(AFFILIATE, "swish");

        assertThat(result.total()).isEqualByComparingTo("16.66");
        assertThat(result.commissionCount()).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(CommissionStatus.PAID.name());
        assertThat(b.getStatus()).isEqualTo(CommissionStatus.PAID.name());
        assertThat(a.getPayoutId()).isNotNull();

        var captor = ArgumentCaptor.forClass(AffiliatePayoutEntity.class);
        verify(payouts).save(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("16.66");
        assertThat(captor.getValue().getStatus()).isEqualTo("PAID");
        assertThat(captor.getValue().getMethod()).isEqualTo("swish");
    }

    @Test
    void payout_fails_when_nothing_is_payable() {
        when(commissions.findByAffiliateIdAndStatus(AFFILIATE, CommissionStatus.APPROVED.name()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.payOut(AFFILIATE, "swish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing payable");
        verify(payouts, never()).save(any());
    }
}
