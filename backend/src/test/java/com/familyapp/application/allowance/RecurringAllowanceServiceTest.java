package com.familyapp.application.allowance;

import com.familyapp.application.allowance.RecurringAllowanceService.AllowanceSpec;
import com.familyapp.application.subscription.SubscriptionService;
import com.familyapp.application.wallet.WalletService;
import com.familyapp.domain.allowance.AllowanceKind;
import com.familyapp.infrastructure.allowance.RecurringAllowanceEntity;
import com.familyapp.infrastructure.allowance.RecurringAllowanceJpaRepository;
import com.familyapp.infrastructure.allowance.RecurringAllowancePaymentEntity;
import com.familyapp.infrastructure.allowance.RecurringAllowancePaymentJpaRepository;
import com.familyapp.infrastructure.family.FamilyEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpHistoryEntity;
import com.familyapp.infrastructure.xp.MemberXpHistoryJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpProgressEntity;
import com.familyapp.infrastructure.xp.MemberXpProgressJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Riktiga pengar, så reglerna pinnas här.
 *
 * Den viktigaste halvan är inte att rätt belopp betalas -- det är att inget betalas
 * två gånger, att en oläsbar nivå skjuter upp i stället för att gissa, och att ett
 * barn inte kan ändra sitt eget belopp.
 */
class RecurringAllowanceServiceTest {

    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID PARENT = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2026, 10, 1);

    private RecurringAllowanceJpaRepository repository;
    private RecurringAllowancePaymentJpaRepository payments;
    private FamilyMemberJpaRepository members;
    private MemberXpHistoryJpaRepository history;
    private MemberXpProgressJpaRepository progress;
    private WalletService wallet;
    private SubscriptionService subscriptions;
    private RecurringAllowanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(RecurringAllowanceJpaRepository.class);
        payments = mock(RecurringAllowancePaymentJpaRepository.class);
        members = mock(FamilyMemberJpaRepository.class);
        history = mock(MemberXpHistoryJpaRepository.class);
        progress = mock(MemberXpProgressJpaRepository.class);
        wallet = mock(WalletService.class);
        subscriptions = mock(SubscriptionService.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptions.isEntitled(any())).thenReturn(true);
        service = new RecurringAllowanceService(
                repository, payments, members, history, progress, wallet, subscriptions);
    }

    private FamilyMemberEntity member(UUID id, String role) {
        var family = new FamilyEntity();
        family.setId(FAMILY);
        var e = new FamilyMemberEntity();
        e.setId(id);
        e.setName(role.equals("CHILD") ? "Ella" : "Patrik");
        e.setRole(role);
        e.setFamily(family);
        when(members.findById(id)).thenReturn(Optional.of(e));
        return e;
    }

    private RecurringAllowanceEntity schedule(AllowanceKind kind) {
        var e = new RecurringAllowanceEntity();
        e.setId(SCHEDULE);
        e.setMemberId(CHILD);
        e.setCreatedByMemberId(PARENT);
        e.setKind(kind.name());
        e.setActive(true);
        e.setNextDueOn(DUE);
        e.setDayOfMonth(1);
        e.setAmount(150);
        e.setLevel1Amount(20);
        e.setLevel2Amount(30);
        e.setLevel3Amount(40);
        e.setLevel4Amount(60);
        e.setLevel5Amount(100);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        when(repository.findById(SCHEDULE)).thenReturn(Optional.of(e));
        return e;
    }

    private RecurringAllowancePaymentEntity savedPayment() {
        var captor = ArgumentCaptor.forClass(RecurringAllowancePaymentEntity.class);
        verify(payments).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ behörighet

    @Test
    void ett_barn_kan_inte_andra_sin_egen_peng() {
        member(CHILD, "CHILD");

        assertThatThrownBy(() -> service.get(CHILD, CHILD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endast en förälder");
    }

    @Test
    void en_foralder_i_en_annan_familj_slapps_inte_in() {
        member(CHILD, "CHILD");
        var stranger = new FamilyMemberEntity();
        var otherFamily = new FamilyEntity();
        otherFamily.setId(UUID.randomUUID());
        stranger.setId(PARENT);
        stranger.setRole("PARENT");
        stranger.setFamily(otherFamily);
        when(members.findById(PARENT)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.get(CHILD, PARENT))
                .hasMessageContaining("Not a member of this family");
    }

    // ------------------------------------------------------------------ validering

    @Test
    void veckopeng_kraver_belopp_och_veckodag() {
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");

        assertThatThrownBy(() -> service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.WEEKLY, null, 5, null, null, null, null, null, null)))
                .hasMessageContaining("större än 0");

        assertThatThrownBy(() -> service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.WEEKLY, 50, null, null, null, null, null, null, null)))
                .hasMessageContaining("veckodag");
    }

    @Test
    void dag_29_till_31_gar_inte_att_valja() {
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");

        assertThatThrownBy(() -> service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.MONTHLY, 150, null, 31, null, null, null, null, null)))
                .hasMessageContaining("mellan 1 och 28");
    }

    @Test
    void alla_fem_nivabelopp_kravs() {
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");

        assertThatThrownBy(() -> service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.LEVEL, null, null, 1, 20, 30, null, 60, 100)))
                .hasMessageContaining("nivå 3");
    }

    @Test
    void noll_kronor_ar_ett_giltigt_nivabelopp() {
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");
        when(repository.findByMemberId(CHILD)).thenReturn(Optional.empty());

        assertThatCode(() -> service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.LEVEL, null, null, 1, 0, 30, 40, 60, 100)))
                .doesNotThrowAnyException();
    }

    @Test
    void forsta_utbetalningen_hamnar_alltid_efter_idag() {
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");
        when(repository.findByMemberId(CHILD)).thenReturn(Optional.empty());

        var saved = service.save(CHILD, PARENT,
                new AllowanceSpec(AllowanceKind.WEEKLY, 50, 5, null, null, null, null, null, null));

        // Att spara ska aldrig betala ut samma dag, hur många gånger man än trycker.
        assertThat(saved.getNextDueOn()).isAfter(LocalDate.now(RecurringAllowanceService.ZONE));
        assertThat(saved.getNextDueOn().getDayOfWeek().getValue()).isEqualTo(5);
    }

    // ----------------------------------------------------------------- utbetalning

    @Test
    void betalar_manadspeng_och_skriver_kvitto() {
        schedule(AllowanceKind.MONTHLY);
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");

        service.payOne(SCHEDULE, DUE);

        verify(wallet).addAllowance(eq(CHILD), eq(150), anyString(), eq(PARENT), eq(null));
        assertThat(savedPayment().getStatus()).isEqualTo("PAID");
        assertThat(savedPayment().getDueDate()).isEqualTo(DUE);
    }

    @Test
    void samma_forfallodag_betalas_aldrig_tva_ganger() {
        var s = schedule(AllowanceKind.MONTHLY);
        member(CHILD, "CHILD");
        when(payments.existsByMemberIdAndDueDate(CHILD, DUE)).thenReturn(true);

        service.payOne(SCHEDULE, DUE);

        verify(wallet, never()).addAllowance(any(), anyInt(), anyString(), any(), any());
        verify(payments, never()).save(any());
        // Men datumet flyttas fram, annars fastnar schemat på en dag som är klar.
        assertThat(s.getNextDueOn()).isAfter(DUE);
    }

    @Test
    void utan_prenumeration_pausas_pengen_men_syns_i_kvittot() {
        var s = schedule(AllowanceKind.MONTHLY);
        member(CHILD, "CHILD");
        when(subscriptions.isEntitled(FAMILY)).thenReturn(false);

        service.payOne(SCHEDULE, DUE);

        verify(wallet, never()).addAllowance(any(), anyInt(), anyString(), any(), any());
        assertThat(savedPayment().getStatus()).isEqualTo("SKIPPED_NO_SUBSCRIPTION");
        assertThat(s.getNextDueOn()).isAfter(DUE);
    }

    @Test
    void nivan_hamtas_ur_historiken() {
        schedule(AllowanceKind.LEVEL);
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");
        var h = new MemberXpHistoryEntity();
        h.setFinalLevel(4);
        when(history.findForMonth(CHILD, 2026, 9)).thenReturn(Optional.of(h));

        service.payOne(SCHEDULE, DUE);

        verify(wallet).addAllowance(eq(CHILD), eq(60), anyString(), eq(PARENT), eq(null));
    }

    @Test
    void nivan_kan_lasas_ur_progress_om_nollstallningen_inte_hunnit_kora() {
        schedule(AllowanceKind.LEVEL);
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");
        when(history.findForMonth(CHILD, 2026, 9)).thenReturn(Optional.empty());
        var p = new MemberXpProgressEntity();
        p.setCurrentLevel(2);
        when(progress.findByMemberIdAndYearAndMonth(CHILD, 2026, 9)).thenReturn(Optional.of(p));

        service.payOne(SCHEDULE, DUE);

        verify(wallet).addAllowance(eq(CHILD), eq(30), anyString(), eq(PARENT), eq(null));
    }

    @Test
    void olasbar_niva_skjuter_upp_i_stallet_for_att_gissa() {
        var s = schedule(AllowanceKind.LEVEL);
        member(CHILD, "CHILD");
        when(history.findForMonth(any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(progress.findByMemberIdAndYearAndMonth(any(), anyInt(), anyInt())).thenReturn(Optional.empty());

        service.payOne(SCHEDULE, DUE);

        verify(wallet, never()).addAllowance(any(), anyInt(), anyString(), any(), any());
        verify(payments, never()).save(any());
        // Datumet står kvar: jobbet kör igen imorgon och hittar nivån då.
        assertThat(s.getNextDueOn()).isEqualTo(DUE);
    }

    @Test
    void noll_kronor_betalas_inte_men_bokfors() {
        var s = schedule(AllowanceKind.LEVEL);
        s.setLevel1Amount(0);
        member(CHILD, "CHILD");
        var h = new MemberXpHistoryEntity();
        h.setFinalLevel(1);
        when(history.findForMonth(CHILD, 2026, 9)).thenReturn(Optional.of(h));

        service.payOne(SCHEDULE, DUE);

        verify(wallet, never()).addAllowance(any(), anyInt(), anyString(), any(), any());
        assertThat(savedPayment().getStatus()).isEqualTo("SKIPPED_ZERO_AMOUNT");
        assertThat(s.getNextDueOn()).isAfter(DUE);
    }

    @Test
    void missade_perioder_betalas_en_gang_inte_en_gang_per_period() {
        var s = schedule(AllowanceKind.WEEKLY);
        s.setKind(AllowanceKind.WEEKLY.name());
        s.setAmount(50);
        s.setNextDueOn(DUE);
        member(CHILD, "CHILD");
        member(PARENT, "PARENT");

        // Tre veckor senare -- servern har varit nere.
        var today = DUE.plusWeeks(3);
        service.payOne(SCHEDULE, today);

        verify(wallet).addAllowance(eq(CHILD), eq(50), anyString(), eq(PARENT), eq(null));
        // En utbetalning, och nästa hamnar framåt i tiden. Tre veckopengar på en gång
        // vore en värre överraskning än två missade.
        assertThat(s.getNextDueOn()).isAfter(today);
    }

    @Test
    void om_foraldern_som_skapade_schemat_ar_borta_tar_en_annan_vuxen_over() {
        var s = schedule(AllowanceKind.MONTHLY);
        s.setCreatedByMemberId(null);
        member(CHILD, "CHILD");
        var other = member(UUID.randomUUID(), "PARENT");
        when(members.findByFamilyId(FAMILY)).thenReturn(List.of(other));

        service.payOne(SCHEDULE, DUE);

        verify(wallet).addAllowance(eq(CHILD), eq(150), anyString(), eq(other.getId()), eq(null));
    }
}
