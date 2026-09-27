package com.familyapp.application.admin;

import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.infrastructure.admin.AdminStatsQueries;
import com.familyapp.infrastructure.admin.AdminStatsQueries.ActivityRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.EventRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.FamilyRow;
import com.familyapp.infrastructure.admin.AdminStatsQueries.XpRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The numbers on the admin statistics page. Sums and shares only: nothing here names or
 * identifies a family, and the only people named are the affiliates, as on the affiliate page.
 *
 * <p>Definitions (the page shows these words, so they are the contract):
 * <ul>
 *   <li><b>children</b> = members with role CHILD or ASSISTANT; <b>adults</b> = PARENT.</li>
 *   <li><b>new this week</b> = families created in the last 7 days; the comparison is the 7 days before.</li>
 *   <li><b>weekly active family</b> = at least one completed chore in the last 7 days.</li>
 *   <li><b>paying</b> = subscription ACTIVE and not comped; <b>comped</b> = is_comped or status COMPED.</li>
 *   <li><b>trials ending</b> = status TRIAL with trial_ends_at within the next 7 days.</li>
 *   <li><b>funnel</b>, over families created in the period: registered → added a child → a child's
 *       phone linked (device token set, the same rule as hasPairedDevice) → any chore completed →
 *       first egg picked (child_pet or pet_history) → a chore completed at least 7 days after signing up.</li>
 *   <li><b>weekly series</b> = the last 12 ISO weeks in Europe/Stockholm, empty weeks included.</li>
 *   <li><b>trial → paid</b> = among non-comped families whose trial ended in the period, the share now ACTIVE.</li>
 *   <li><b>revenue</b> = the sum of RevenueCat's {@code price} (USD, before store fees) over
 *       INITIAL_PURCHASE and RENEWAL events in the period; sandbox events are left out everywhere.</li>
 *   <li><b>chores per child and day</b> = completions in the period / children with an active chore / days.</li>
 *   <li><b>level 5</b> = children with this month's level ≥ 5 among children with XP this month;
 *       stars there = min(5, floor((xp − 125) / 50)).</li>
 * </ul>
 */
@Service
public class AdminStatsService {

    public static final List<Integer> PERIODS = List.of(7, 30, 90);
    static final ZoneId WEEK_ZONE = ZoneId.of("Europe/Stockholm");
    static final int WEEKS = 12;
    private static final int MAX_LEVEL_XP = 125;
    private static final int XP_PER_STAR = 50;
    private static final int MAX_STARS = 5;

    private final AdminStatsQueries queries;
    private final AffiliateService affiliateService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AdminStatsService(AdminStatsQueries queries, AffiliateService affiliateService, ObjectMapper objectMapper) {
        this(queries, affiliateService, objectMapper, Clock.systemUTC());
    }

    AdminStatsService(AdminStatsQueries queries, AffiliateService affiliateService, ObjectMapper objectMapper, Clock clock) {
        this.queries = queries;
        this.affiliateService = affiliateService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** All the page's numbers for a period of {@code days} (7, 30 or 90). */
    @Transactional(readOnly = true)
    public AdminStats stats(int days) {
        if (!PERIODS.contains(days)) {
            throw new IllegalArgumentException("days must be one of " + PERIODS);
        }
        var now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        var periodStart = now.minusDays(days);
        var weekAgo = now.minusDays(7);
        var families = queries.families(weekAgo);

        var kpis = kpis(families, now);
        var funnel = funnel(families.stream().filter(f -> !f.createdAt().isBefore(periodStart)).toList());
        var weekStarts = weekStarts(now);
        var seriesStart = weekStarts.get(0).atStartOfDay(WEEK_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var weekly = weekly(weekStarts, families.stream().map(FamilyRow::createdAt).toList(),
                queries.activityByHour(seriesStart));
        var subscriptions = subscriptions(families, queries.subscriptionEvents(periodStart), now, periodStart);
        var distributions = distributions(families, queries.adultLanguages());
        var engagement = engagement(days, now, families);
        return new AdminStats(days, now, kpis, funnel, weekly, subscriptions, distributions, engagement, affiliates());
    }

    // ------------------------------------------------------------------ sections

    Kpis kpis(List<FamilyRow> families, LocalDateTime now) {
        var weekAgo = now.minusDays(7);
        var twoWeeksAgo = now.minusDays(14);
        long total = families.size();
        long active = count(families, FamilyRow::activeLast7Days);
        var ending = families.stream()
                .filter(f -> "TRIAL".equals(f.subscriptionStatus()) && f.trialEndsAt() != null
                        && !f.trialEndsAt().isBefore(now) && !f.trialEndsAt().isAfter(now.plusDays(7)))
                .toList();
        return new Kpis(
                total,
                families.stream().mapToLong(FamilyRow::children).sum(),
                families.stream().mapToLong(FamilyRow::adults).sum(),
                count(families, f -> !f.createdAt().isBefore(weekAgo)),
                count(families, f -> !f.createdAt().isBefore(twoWeeksAgo) && f.createdAt().isBefore(weekAgo)),
                active,
                rate(active, total),
                count(families, AdminStatsService::isPaying),
                count(families, AdminStatsService::isComped),
                ending.size(),
                count(ending, FamilyRow::activeLast7Days));
    }

    Funnel funnel(List<FamilyRow> cohort) {
        return new Funnel(
                cohort.size(),
                count(cohort, f -> f.children() > 0),
                count(cohort, FamilyRow::childPhoneLinked),
                count(cohort, FamilyRow::anyChoreDone),
                count(cohort, FamilyRow::firstEggPicked),
                count(cohort, FamilyRow::choreDoneAfterFirstWeek));
    }

    /** Mondays of the last {@link #WEEKS} ISO weeks in Stockholm, oldest first, this week last. */
    static List<LocalDate> weekStarts(LocalDateTime nowUtc) {
        var today = nowUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(WEEK_ZONE).toLocalDate();
        var thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weeks = new ArrayList<LocalDate>(WEEKS);
        for (int i = WEEKS - 1; i >= 0; i--) {
            weeks.add(thisMonday.minusWeeks(i));
        }
        return weeks;
    }

    /** The Stockholm week (its Monday) a UTC time falls in. */
    static LocalDate weekOf(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(WEEK_ZONE).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static List<Week> weekly(List<LocalDate> weekStarts, List<LocalDateTime> familyCreated, List<ActivityRow> activity) {
        var newByWeek = new LinkedHashMap<LocalDate, Long>();
        var activeByWeek = new LinkedHashMap<LocalDate, Set<String>>();
        weekStarts.forEach(w -> { newByWeek.put(w, 0L); activeByWeek.put(w, new HashSet<>()); });
        familyCreated.stream().filter(Objects::nonNull).map(AdminStatsService::weekOf)
                .filter(newByWeek::containsKey).forEach(w -> newByWeek.merge(w, 1L, Long::sum));
        activity.forEach(a -> {
            var set = activeByWeek.get(weekOf(a.hour()));
            if (set != null) {
                set.add(a.familyKey());
            }
        });
        return weekStarts.stream()
                .map(w -> new Week(w, w.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), newByWeek.get(w), activeByWeek.get(w).size()))
                .toList();
    }

    Subscriptions subscriptions(List<FamilyRow> families, List<EventRow> events, LocalDateTime now, LocalDateTime periodStart) {
        var trialsEnded = families.stream()
                .filter(f -> !isComped(f) && f.trialEndsAt() != null
                        && !f.trialEndsAt().isBefore(periodStart) && !f.trialEndsAt().isAfter(now))
                .toList();
        var created = families.stream().filter(f -> !f.createdAt().isBefore(periodStart)).toList();
        var production = events.stream().map(this::parse).filter(e -> !e.sandbox()).toList();
        var revenue = production.stream()
                .filter(e -> "INITIAL_PURCHASE".equals(e.type()) || "RENEWAL".equals(e.type()))
                .map(ParsedEvent::priceUsd).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Subscriptions(
                trialsEnded.size(),
                rate(count(trialsEnded, AdminStatsService::isPaying), trialsEnded.size()),
                production.stream().filter(e -> "INITIAL_PURCHASE".equals(e.type())).count(),
                production.stream().filter(e -> "CANCELLATION".equals(e.type())).count(),
                production.stream().filter(e -> "BILLING_ISSUE".equals(e.type())).count(),
                revenue,
                "USD",
                rate(count(created, FamilyRow::referred), created.size()));
    }

    Distributions distributions(List<FamilyRow> families, List<String> adultLanguages) {
        return new Distributions(
                tally(adultLanguages, l -> l == null || l.isBlank() ? "device" : l),
                tally(families.stream().map(FamilyRow::currency).toList(), c -> c == null ? "SEK" : c),
                tally(families.stream().filter(AdminStatsService::isPaying).map(FamilyRow::platform).toList(),
                        p -> p == null ? "UNKNOWN" : p),
                tally(families.stream().map(f -> f.referred() ? "referred" : "organic").toList(), Function.identity()));
    }

    Engagement engagement(int days, LocalDateTime now, List<FamilyRow> families) {
        var periodStart = now.minusDays(days);
        long children = families.stream().mapToLong(FamilyRow::children).sum();
        long choreChildren = queries.childrenWithActiveChore();
        long completions = queries.completionsSince(periodStart);
        var xp = queries.childXp(now.getYear(), now.getMonthValue());
        var atMax = xp.stream().filter(r -> r.level() >= 5).toList();
        return new Engagement(
                choreChildren == 0 ? 0 : (double) completions / choreChildren / days,
                rate(atMax.size(), xp.size()),
                atMax.stream().mapToInt(AdminStatsService::stars).average().orElse(0),
                queries.adventuresSince(periodStart),
                rate(count(families, FamilyRow::autoAllowance), families.size()),
                rate(queries.childrenWithSavingsGoal(), children));
    }

    List<AffiliateStat> affiliates() {
        var paying = queries.payingReferralsByAffiliate();
        return affiliateService.listAffiliates().stream()
                .map(a -> {
                    long payingCount = paying.getOrDefault(a.id().toString(), 0L);
                    return new AffiliateStat(a.name(), a.referralCode(), a.referralCount(), payingCount,
                            rate(payingCount, a.referralCount()), a.payable(), a.paidOut());
                })
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    /** A share in 0..1; zero when there is nothing to divide by. */
    static double rate(long part, long whole) {
        return whole <= 0 ? 0 : (double) part / whole;
    }

    static int stars(XpRow row) {
        return Math.max(0, Math.min(MAX_STARS, (row.xp() - MAX_LEVEL_XP) / XP_PER_STAR));
    }

    static boolean isComped(FamilyRow f) {
        return f.comped() || "COMPED".equals(f.subscriptionStatus());
    }

    static boolean isPaying(FamilyRow f) {
        return "ACTIVE".equals(f.subscriptionStatus()) && !isComped(f);
    }

    private static <T> long count(List<T> rows, Predicate<T> test) {
        return rows.stream().filter(test).count();
    }

    /** Counts per key, largest first. */
    private static <T> List<Share> tally(List<T> values, Function<T, String> key) {
        Map<String, Long> counts = values.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Share(e.getKey(), e.getValue()))
                .toList();
    }

    private record ParsedEvent(String type, boolean sandbox, BigDecimal priceUsd) {}

    private ParsedEvent parse(EventRow row) {
        try {
            JsonNode event = row.payload() == null ? null : objectMapper.readTree(row.payload()).path("event");
            if (event == null || event.isMissingNode()) {
                return new ParsedEvent(row.type(), false, null);
            }
            var price = event.path("price");
            return new ParsedEvent(row.type(),
                    "SANDBOX".equalsIgnoreCase(event.path("environment").asText("")),
                    price.isNumber() ? price.decimalValue() : null);
        } catch (Exception e) {
            // A payload that does not parse still counts as an event; it just carries no price.
            return new ParsedEvent(row.type(), false, null);
        }
    }

    // ------------------------------------------------------------------ response

    public record AdminStats(int days, LocalDateTime generatedAtUtc, Kpis kpis, Funnel funnel, List<Week> weekly,
                             Subscriptions subscriptions, Distributions distributions, Engagement engagement,
                             List<AffiliateStat> affiliates) {}

    public record Kpis(long families, long children, long adults, long newThisWeek, long newPrevWeek,
                       long weeklyActive, double weeklyActiveShare, long paying, long comped,
                       long trialsEndingIn7Days, long trialsEndingActive) {}

    public record Funnel(long registered, long addedChild, long linkedChildPhone, long firstChoreDone,
                         long pickedFirstEgg, long activeAfter7Days) {}

    public record Week(LocalDate weekStart, int isoWeek, long newFamilies, long activeFamilies) {}

    public record Subscriptions(long trialsEnded, double trialToPaidRate, long newPaying, long cancellations,
                                long billingIssues, BigDecimal revenue, String revenueCurrency, double referredShare) {}

    public record Share(String key, long count) {}

    public record Distributions(List<Share> adultLanguage, List<Share> currency, List<Share> payingPlatform,
                                List<Share> source) {}

    public record Engagement(double choresPerChildPerDay, double level5Share, double avgStarsAtLevel5,
                             long adventuresSent, double familiesWithAutoAllowance, double childrenWithSavingsGoal) {}

    public record AffiliateStat(String name, String referralCode, long referrals, long paying, double conversion,
                                BigDecimal payable, BigDecimal paidOut) {}
}
