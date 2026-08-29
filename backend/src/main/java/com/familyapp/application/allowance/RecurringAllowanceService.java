package com.familyapp.application.allowance;

import com.familyapp.application.subscription.SubscriptionService;
import com.familyapp.application.wallet.WalletService;
import com.familyapp.domain.allowance.AllowanceKind;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.allowance.RecurringAllowanceEntity;
import com.familyapp.infrastructure.allowance.RecurringAllowanceJpaRepository;
import com.familyapp.infrastructure.allowance.RecurringAllowancePaymentEntity;
import com.familyapp.infrastructure.allowance.RecurringAllowancePaymentJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpHistoryJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpProgressJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Automatisk vecko- och månadspeng.
 *
 * Tre saker styr hur det här är byggt, och alla tre kommer av att det är riktiga
 * pengar snarare än poäng.
 *
 * Jobbet frågar aldrig "är det fredag idag". Det frågar "vad är förfallet och
 * obetalt". Railway startar om containern ofta, och ett jobb som bara tittar på
 * dagens datum hoppar tyst över en vecka om omstarten råkar ligga fel -- samma mängd
 * kod, helt annat beteende när något går sönder.
 *
 * Dubbelbetalning stoppas av databasen, inte av den här klassen. Kvittoraden skrivs i
 * samma transaktion som pengarna, och det unika indexet på (member_id, due_date)
 * betyder att en andra körning för samma dag krockar i stället för att betala igen.
 * En kontroll i Java hade räckt ända tills två körningar överlappade.
 *
 * Allt datum räknas i Europe/Stockholm. Servern går i UTC, och en veckopeng på
 * söndagar skulle annars betalas på måndagar halva året -- exakt det fel husdjurets
 * månad redan har.
 */
@Service
public class RecurringAllowanceService {

    private static final Logger log = LoggerFactory.getLogger(RecurringAllowanceService.class);

    /** Familjerna är svenska och pengar ska komma på rätt dag. */
    static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");

    /** Högsta dag i månaden som finns varje månad. Se V46. */
    private static final int MAX_DAY_OF_MONTH = 28;

    private static final String PAID = "PAID";
    private static final String SKIPPED_NO_SUBSCRIPTION = "SKIPPED_NO_SUBSCRIPTION";
    private static final String SKIPPED_ZERO = "SKIPPED_ZERO_AMOUNT";

    private final RecurringAllowanceJpaRepository repository;
    private final RecurringAllowancePaymentJpaRepository paymentRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final MemberXpHistoryJpaRepository historyRepository;
    private final MemberXpProgressJpaRepository progressRepository;
    private final WalletService walletService;
    private final SubscriptionService subscriptionService;

    public RecurringAllowanceService(
            RecurringAllowanceJpaRepository repository,
            RecurringAllowancePaymentJpaRepository paymentRepository,
            FamilyMemberJpaRepository memberRepository,
            MemberXpHistoryJpaRepository historyRepository,
            MemberXpProgressJpaRepository progressRepository,
            WalletService walletService,
            SubscriptionService subscriptionService
    ) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.historyRepository = historyRepository;
        this.progressRepository = progressRepository;
        this.walletService = walletService;
        this.subscriptionService = subscriptionService;
    }

    /** Allt som är förfallet och obetalt per ett datum. */
    @Transactional(readOnly = true)
    public java.util.List<UUID> dueScheduleIds(LocalDate date) {
        return repository.findByActiveTrueAndNextDueOnLessThanEqual(date).stream()
                .map(RecurringAllowanceEntity::getId)
                .toList();
    }

    // ---------------------------------------------------------------- läsa och skriva

    @Transactional(readOnly = true)
    public RecurringAllowanceEntity get(UUID memberId, UUID requesterId) {
        requireParentOf(memberId, requesterId);
        return repository.findByMemberId(memberId).orElse(null);
    }

    /**
     * Sparar eller ersätter barnets schema.
     *
     * next_due_on läggs alltid strikt efter idag. Att ställa in en veckopeng på en
     * fredag ska inte betala ut pengar samma fredag -- en förälder som råkar spara
     * två gånger ska inte kunna ge dubbelt av misstag.
     */
    @Transactional
    public RecurringAllowanceEntity save(UUID memberId, UUID requesterId, AllowanceSpec spec) {
        var requester = requireParentOf(memberId, requesterId);
        validate(spec);

        var today = LocalDate.now(ZONE);
        var entity = repository.findByMemberId(memberId).orElseGet(() -> {
            var fresh = new RecurringAllowanceEntity();
            fresh.setId(UUID.randomUUID());
            fresh.setMemberId(memberId);
            fresh.setCreatedAt(OffsetDateTime.now());
            return fresh;
        });

        entity.setCreatedByMemberId(requester.getId());
        entity.setKind(spec.kind().name());
        entity.setAmount(spec.amount());
        entity.setWeekday(spec.weekday());
        entity.setDayOfMonth(spec.dayOfMonth());
        entity.setLevel1Amount(spec.levelAmount(1));
        entity.setLevel2Amount(spec.levelAmount(2));
        entity.setLevel3Amount(spec.levelAmount(3));
        entity.setLevel4Amount(spec.levelAmount(4));
        entity.setLevel5Amount(spec.levelAmount(5));
        entity.setActive(true);
        entity.setNextDueOn(firstDueAfter(spec, today));
        entity.setUpdatedAt(OffsetDateTime.now());

        var saved = repository.save(entity);
        log.info("Automatisk utbetalning {} för medlem {}, nästa utbetalning {}",
                spec.kind(), memberId, saved.getNextDueOn());
        return saved;
    }

    @Transactional
    public void disable(UUID memberId, UUID requesterId) {
        requireParentOf(memberId, requesterId);
        repository.findByMemberId(memberId).ifPresent(entity -> {
            entity.setActive(false);
            entity.setUpdatedAt(OffsetDateTime.now());
            repository.save(entity);
            log.info("Automatisk utbetalning avstängd för medlem {}", memberId);
        });
    }

    // ---------------------------------------------------------------------- utbetalning

    /**
     * En förfallodag, i en egen transaktion.
     *
     * Egen transaktion så att ett fel för ett barn inte rullar tillbaka utbetalningar
     * som redan lyckats för syskonen.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void payOne(UUID scheduleId, LocalDate today) {
        var schedule = repository.findById(scheduleId).orElse(null);
        if (schedule == null || !schedule.isActive()) {
            return;
        }
        var memberId = schedule.getMemberId();
        var dueDate = schedule.getNextDueOn();

        if (paymentRepository.existsByMemberIdAndDueDate(memberId, dueDate)) {
            // Redan hanterad -- en tidigare körning kom hit först.
            advance(schedule, today);
            return;
        }

        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return;
        }
        var familyId = member.getFamily() != null ? member.getFamily().getId() : null;

        if (familyId != null && !subscriptionService.isEntitled(familyId)) {
            // Pausas hellre än betalas: automatisk administration ska inte fortsätta
            // när föräldern förlorat rätten att administrera. Raden skrivs ändå, så
            // att en förälder som undrar får ett svar i stället för ett hål.
            record(memberId, dueDate, 0, SKIPPED_NO_SUBSCRIPTION, "Ingen aktiv prenumeration");
            advance(schedule, today);
            log.info("Automatisk utbetalning pausad för medlem {} ({}): ingen prenumeration", memberId, dueDate);
            return;
        }

        Integer amount = resolveAmount(schedule, dueDate);
        if (amount == null) {
            // Nivån för månaden som gick gick inte att läsa ännu. Flytta INTE fram
            // datumet -- jobbet kör igen imorgon och hittar den då.
            log.warn("Automatisk utbetalning uppskjuten för medlem {} ({}): nivån går inte att avgöra ännu",
                    memberId, dueDate);
            return;
        }
        if (amount <= 0) {
            record(memberId, dueDate, 0, SKIPPED_ZERO, "Beloppet för nivån är 0 kr");
            advance(schedule, today);
            return;
        }

        var giver = resolveGiver(schedule, member);
        if (giver == null) {
            log.error("Automatisk utbetalning för medlem {} saknar vuxen avsändare", memberId);
            return;
        }

        walletService.addAllowance(memberId, amount, describe(schedule), giver.getId(), null);
        record(memberId, dueDate, amount, PAID, null);
        advance(schedule, today);
        log.info("Automatisk utbetalning: {} kr till medlem {} för {}", amount, memberId, dueDate);
    }

    // ------------------------------------------------------------------------ internt

    private Integer resolveAmount(RecurringAllowanceEntity schedule, LocalDate dueDate) {
        var kind = AllowanceKind.valueOf(schedule.getKind());
        if (kind != AllowanceKind.LEVEL) {
            return schedule.getAmount();
        }
        var level = levelForMonthBefore(schedule.getMemberId(), dueDate);
        return level == null ? null : schedule.amountForLevel(level);
    }

    /**
     * Nivån barnet nådde under månaden som just tog slut.
     *
     * Historiken först. XP-nollställningen skriver om progress-raden på plats i
     * stället för att skapa en ny, så efter den finns förra månaden bara i historiken
     * -- men körs den här före nollställningen står förra månaden fortfarande kvar i
     * progress. Båda ordningarna funkar; ingen av dem läser fel månad.
     */
    private Integer levelForMonthBefore(UUID memberId, LocalDate dueDate) {
        var previous = dueDate.minusMonths(1);
        var year = previous.getYear();
        var month = previous.getMonthValue();

        var fromHistory = historyRepository.findForMonth(memberId, year, month);
        if (fromHistory.isPresent()) {
            return fromHistory.get().getFinalLevel();
        }
        return progressRepository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .map(p -> p.getCurrentLevel())
                .orElse(null);
    }

    /**
     * Föräldern som satte upp schemat, annars vilken förälder som helst i familjen.
     *
     * addAllowance kräver en vuxen avsändare, och den som skapade schemat kan ha
     * lämnat familjen sedan dess -- då nollas kolumnen av en FK. Att låta pengen
     * utebli för att en förälder tagits bort vore fel svar.
     */
    private FamilyMemberEntity resolveGiver(RecurringAllowanceEntity schedule, FamilyMemberEntity child) {
        var creatorId = schedule.getCreatedByMemberId();
        if (creatorId != null) {
            var creator = memberRepository.findById(creatorId).orElse(null);
            if (creator != null && isAdult(creator)) {
                return creator;
            }
        }
        var familyId = child.getFamily() != null ? child.getFamily().getId() : null;
        if (familyId == null) {
            return null;
        }
        return memberRepository.findByFamilyId(familyId).stream()
                .filter(this::isAdult)
                .findFirst()
                .orElse(null);
    }

    private boolean isAdult(FamilyMemberEntity member) {
        return Role.PARENT.name().equals(member.getRole())
                || Role.ASSISTANT.name().equals(member.getRole());
    }

    private void record(UUID memberId, LocalDate dueDate, int amount, String status, String note) {
        var payment = new RecurringAllowancePaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setMemberId(memberId);
        payment.setDueDate(dueDate);
        payment.setAmount(amount);
        payment.setStatus(status);
        payment.setNote(note);
        payment.setCreatedAt(OffsetDateTime.now());
        paymentRepository.save(payment);
    }

    /**
     * Nästa förfallodag, alltid strikt efter idag.
     *
     * Om flera perioder missats betalas en gång och resten hoppas över. Ett längre
     * glapp beror nästan alltid på att något varit trasigt, och att då plötsligt
     * betala tre veckopengar på en gång är en värre överraskning än att missa två.
     */
    private void advance(RecurringAllowanceEntity schedule, LocalDate today) {
        var kind = AllowanceKind.valueOf(schedule.getKind());
        var next = schedule.getNextDueOn();
        var guard = 0;
        while (!next.isAfter(today) && guard++ < 400) {
            next = kind == AllowanceKind.WEEKLY ? next.plusWeeks(1) : next.plusMonths(1);
        }
        if (guard > 1) {
            log.warn("Automatisk utbetalning för medlem {} hoppade över {} perioder",
                    schedule.getMemberId(), guard - 1);
        }
        schedule.setNextDueOn(next);
        schedule.setUpdatedAt(OffsetDateTime.now());
        repository.save(schedule);
    }

    private LocalDate firstDueAfter(AllowanceSpec spec, LocalDate today) {
        if (spec.kind() == AllowanceKind.WEEKLY) {
            var date = today.plusDays(1);
            while (date.getDayOfWeek().getValue() != spec.weekday()) {
                date = date.plusDays(1);
            }
            return date;
        }
        var day = spec.dayOfMonth();
        var candidate = today.withDayOfMonth(day);
        return candidate.isAfter(today) ? candidate : candidate.plusMonths(1);
    }

    private String describe(RecurringAllowanceEntity schedule) {
        return switch (AllowanceKind.valueOf(schedule.getKind())) {
            case WEEKLY -> "Veckopeng";
            case MONTHLY -> "Månadspeng";
            case LEVEL -> "Månadspeng efter nivå";
        };
    }

    private void validate(AllowanceSpec spec) {
        if (spec == null || spec.kind() == null) {
            throw new IllegalArgumentException("Välj vecko- eller månadspeng");
        }
        switch (spec.kind()) {
            case WEEKLY -> {
                requireAmount(spec.amount());
                if (spec.weekday() == null || spec.weekday() < 1 || spec.weekday() > 7) {
                    throw new IllegalArgumentException("Välj en veckodag");
                }
            }
            case MONTHLY -> {
                requireAmount(spec.amount());
                requireDayOfMonth(spec.dayOfMonth());
            }
            case LEVEL -> {
                requireDayOfMonth(spec.dayOfMonth());
                for (int level = 1; level <= 5; level++) {
                    var value = spec.levelAmount(level);
                    if (value == null || value < 0) {
                        throw new IllegalArgumentException("Fyll i ett belopp för nivå " + level);
                    }
                }
            }
        }
    }

    private void requireAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Beloppet måste vara större än 0");
        }
    }

    private void requireDayOfMonth(Integer day) {
        if (day == null || day < 1 || day > MAX_DAY_OF_MONTH) {
            throw new IllegalArgumentException("Välj en dag mellan 1 och " + MAX_DAY_OF_MONTH);
        }
    }

    /**
     * Bara en förälder i samma familj, och aldrig barnet självt.
     *
     * Att dölja raden i barnets vy räcker inte: barnet har en device-token och kan
     * anropa endpointen direkt. Den här appen hade tretton endpoints som antog att
     * klienten inte skulle fråga.
     */
    private FamilyMemberEntity requireParentOf(UUID memberId, UUID requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("Device token is required");
        }
        var requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        if (!Role.PARENT.name().equals(requester.getRole())) {
            throw new IllegalArgumentException("Endast en förälder kan ändra automatisk utbetalning");
        }
        var child = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found"));
        var requesterFamily = requester.getFamily() != null ? requester.getFamily().getId() : null;
        var childFamily = child.getFamily() != null ? child.getFamily().getId() : null;
        if (requesterFamily == null || !requesterFamily.equals(childFamily)) {
            throw new IllegalArgumentException("Not a member of this family");
        }
        return requester;
    }

    /** Det en förälder skickar in. Namnen speglar skärmen, inte tabellen. */
    public record AllowanceSpec(
            AllowanceKind kind,
            Integer amount,
            Integer weekday,
            Integer dayOfMonth,
            Integer level1,
            Integer level2,
            Integer level3,
            Integer level4,
            Integer level5
    ) {
        Integer levelAmount(int level) {
            return switch (level) {
                case 1 -> level1;
                case 2 -> level2;
                case 3 -> level3;
                case 4 -> level4;
                case 5 -> level5;
                default -> null;
            };
        }

        @Override
        public String toString() {
            return "AllowanceSpec[" + kind + "]";
        }
    }
}
