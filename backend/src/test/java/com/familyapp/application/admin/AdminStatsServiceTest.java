package com.familyapp.application.admin;

import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.infrastructure.admin.AdminStatsQueries;
import com.familyapp.infrastructure.admin.AdminStatsQueries.ActivityRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.EventRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.FamilyRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.XpRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The page's arithmetic: week buckets, shares with empty denominators, and the definitions. */
class AdminStatsServiceTest {

    /** Wednesday 24 Sep 2026, 10:00 UTC (ISO week 39). */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 10, 0);

    private AdminStatsQueries queries;
    private AffiliateService affiliates;
    private AdminStatsService service;

    @BeforeEach
    void setUp() {
        queries = mock(AdminStatsQueries.class);
        affiliates = mock(AffiliateService.class);
        var clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new AdminStatsService(queries, affiliates, new ObjectMapper(), clock);
        when(affiliates.listAffiliates()).thenReturn(List.of());
        when(queries.payingReferralsByAffiliate()).thenReturn(Map.of());
    }

    @Test
    void twelveWeeksEndingThisWeekStartingOnMonday() {
        var weeks = AdminStatsService.weekStarts(NOW);

        assertThat(weeks).hasSize(12);
        assertThat(weeks.get(11)).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(weeks.get(0)).isEqualTo(LocalDate.of(2026, 7, 6));
    }

    @Test
    void weeksWithNothingInThemStillAppearAsZero() {
        var weeks = AdminStatsService.weekStarts(NOW);
        var created = List.of(LocalDateTime.of(2026, 9, 22, 8, 0), LocalDateTime.of(2026, 9, 23, 8, 0));
        var activity = List.of(
                new ActivityRow("a", LocalDateTime.of(2026, 9, 22, 8, 0)),
                new ActivityRow("a", LocalDateTime.of(2026, 9, 23, 9, 0)), // same family, same week: once
                new ActivityRow("b", LocalDateTime.of(2026, 9, 23, 9, 0)));

        var series = AdminStatsService.weekly(weeks, created, activity);

        assertThat(series).hasSize(12);
        assertThat(series.get(11).newFamilies()).isEqualTo(2);
        assertThat(series.get(11).activeFamilies()).isEqualTo(2);
        assertThat(series.get(11).isoWeek()).isEqualTo(39);
        assertThat(series.subList(0, 11)).allSatisfy(w -> {
            assertThat(w.newFamilies()).isZero();
            assertThat(w.activeFamilies()).isZero();
        });
    }

    @Test
    void sundayNightUtcIsStillSundayInStockholmUntilTwentyTwo() {
        // 21:30 UTC on Sunday 20 Sep is 23:30 in Stockholm: week 38. 22:30 UTC is Monday: week 39.
        assertThat(AdminStatsService.weekOf(LocalDateTime.of(2026, 9, 20, 21, 30))).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(AdminStatsService.weekOf(LocalDateTime.of(2026, 9, 20, 22, 30))).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void sharesOfNothingAreZeroNotAnError() {
        assertThat(AdminStatsService.rate(0, 0)).isZero();
        assertThat(AdminStatsService.rate(3, 0)).isZero();
        assertThat(AdminStatsService.rate(1, 4)).isEqualTo(0.25);
    }

    @Test
    void anEmptyDatabaseGivesZerosEverywhere() {
        when(queries.families(any())).thenReturn(List.of());

        var stats = service.stats(30);

        assertThat(stats.kpis().families()).isZero();
        assertThat(stats.kpis().weeklyActiveShare()).isZero();
        assertThat(stats.funnel().registered()).isZero();
        assertThat(stats.weekly()).hasSize(12);
        assertThat(stats.subscriptions().trialToPaidRate()).isZero();
        assertThat(stats.subscriptions().revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.engagement().choresPerChildPerDay()).isZero();
        assertThat(stats.engagement().level5Share()).isZero();
    }

    @Test
    void onlyThreePeriodsAreAllowed() {
        assertThatThrownBy(() -> service.stats(14)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionsFollowTheJavadoc() {
        var paying = family(NOW.minusDays(40), "ACTIVE", false, null, true);
        var comped = family(NOW.minusDays(3), "COMPED", false, null, false);
        var compedFlag = family(NOW.minusDays(3), "ACTIVE", true, null, false);
        var endingSoon = family(NOW.minusDays(26), "TRIAL", false, NOW.plusDays(4), true);
        when(queries.families(any())).thenReturn(List.of(paying, comped, compedFlag, endingSoon));
        when(queries.subscriptionEvents(any())).thenReturn(List.of(
                new EventRow("INITIAL_PURCHASE", "{\"event\":{\"price\":2.99,\"environment\":\"PRODUCTION\"}}"),
                new EventRow("RENEWAL", "{\"event\":{\"price\":2.99,\"environment\":\"SANDBOX\"}}"),
                new EventRow("CANCELLATION", "{\"event\":{}}")));
        when(queries.childXp(anyInt(), anyInt())).thenReturn(List.of(new XpRow(5, 230), new XpRow(3, 40)));

        var stats = service.stats(30);

        assertThat(stats.kpis().paying()).isEqualTo(1);
        assertThat(stats.kpis().comped()).isEqualTo(2);
        assertThat(stats.kpis().newThisWeek()).isEqualTo(2);
        assertThat(stats.kpis().trialsEndingIn7Days()).isEqualTo(1);
        assertThat(stats.kpis().trialsEndingActive()).isEqualTo(1);
        assertThat(stats.subscriptions().newPaying()).isEqualTo(1);
        assertThat(stats.subscriptions().cancellations()).isEqualTo(1);
        // The sandbox renewal is left out of the revenue.
        assertThat(stats.subscriptions().revenue()).isEqualByComparingTo("2.99");
        assertThat(stats.engagement().level5Share()).isEqualTo(0.5);
        // 230 xp: floor((230 - 125) / 50) = 2 stars.
        assertThat(stats.engagement().avgStarsAtLevel5()).isEqualTo(2.0);
        assertThat(stats.distributions().payingPlatform()).hasSize(1);
    }

    private static FamilyRow family(LocalDateTime created, String status, boolean comped,
                                    LocalDateTime trialEnds, boolean active) {
        return new FamilyRow(created, "SEK", 1, 1, true, true, true, false, active, false,
                status, comped, trialEnds, "IOS", false);
    }
}
