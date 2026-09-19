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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Accrues affiliate commission from paid subscription periods, and reverses it on refund.
 *
 * Called from {@link com.familyapp.application.subscription.SubscriptionWebhookService} inside
 * the same transaction as the subscription update, so accrual and the purchase it derives from
 * commit together. The methods never throw for missing or non-qualifying data -- an unattributed
 * family, a trial period, or a missing price simply accrues nothing -- so the subscription path
 * is never broken by the affiliate feature.
 *
 * Money: commission is a share (per-affiliate {@code commission_pct}) of the NET the developer
 * keeps after store fees -- {@code price * takehome_percentage} from RevenueCat -- for each paid
 * period, up to {@code commission_month_cap} periods per referred family.
 */
@Service
public class AffiliateCommissionService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateCommissionService.class);

    /** These statuses can still be reversed by a refund. */
    private static final List<String> REVERSIBLE =
            List.of(CommissionStatus.PENDING.name(), CommissionStatus.APPROVED.name());

    private final AffiliateReferralJpaRepository referralRepository;
    private final AffiliateCommissionJpaRepository commissionRepository;
    private final AffiliateJpaRepository affiliateRepository;
    /** Fallback share the developer keeps if RevenueCat omits takehome_percentage. */
    private final BigDecimal defaultTakehomeRatio;

    public AffiliateCommissionService(
            AffiliateReferralJpaRepository referralRepository,
            AffiliateCommissionJpaRepository commissionRepository,
            AffiliateJpaRepository affiliateRepository,
            @Value("${kidquest.affiliate.default-takehome-ratio:0.85}") BigDecimal defaultTakehomeRatio
    ) {
        this.referralRepository = referralRepository;
        this.commissionRepository = commissionRepository;
        this.affiliateRepository = affiliateRepository;
        this.defaultTakehomeRatio = defaultTakehomeRatio;
    }

    /** Accrue commission for a paid period, if the family is attributed and the cap allows. */
    public void recordPaidPeriod(UUID familyId, RevenueCatEvent.Event event) {
        if (familyId == null || event == null || !isPaidPeriod(event)) {
            return;
        }
        var eventId = event.id();
        if (eventId == null || eventId.isBlank() || commissionRepository.existsBySubscriptionEventRef(eventId)) {
            return;
        }
        var referral = referralRepository.findByFamilyId(familyId).orElse(null);
        if (referral == null) {
            return;
        }
        var affiliate = affiliateRepository.findById(referral.getAffiliateId()).orElse(null);
        if (affiliate == null || AffiliateStatus.PAUSED.name().equalsIgnoreCase(affiliate.getStatus())) {
            return;
        }

        long already = commissionRepository.countByAffiliateIdAndFamilyId(affiliate.getId(), familyId);
        if (already >= affiliate.getCommissionMonthCap()) {
            log.info("Affiliate {} hit the {}-period cap for family {}; no commission accrued",
                    affiliate.getId(), affiliate.getCommissionMonthCap(), familyId);
            return;
        }

        var amount = commissionAmount(event, affiliate);
        if (amount.signum() <= 0) {
            return;
        }

        var commission = new AffiliateCommissionEntity();
        commission.setId(UUID.randomUUID());
        commission.setAffiliateId(affiliate.getId());
        commission.setFamilyId(familyId);
        commission.setSubscriptionEventRef(eventId);
        commission.setPeriodIndex((int) already + 1);
        commission.setAmount(amount);
        commission.setCurrency(currencyOf(event));
        commission.setStatus(CommissionStatus.PENDING.name());
        commission.setEarnedAt(OffsetDateTime.now());
        commissionRepository.save(commission);

        log.info("Accrued {} {} commission for affiliate {} (family {}, period {})",
                amount, commission.getCurrency(), affiliate.getId(), familyId, commission.getPeriodIndex());
    }

    /** Reverse the most recent still-reversible commission for a refunded family. */
    public void clawback(UUID familyId, String reason) {
        if (familyId == null) {
            return;
        }
        Optional<AffiliateCommissionEntity> latest =
                commissionRepository.findFirstByFamilyIdAndStatusInOrderByEarnedAtDesc(familyId, REVERSIBLE);
        latest.ifPresent(c -> {
            c.setStatus(CommissionStatus.CLAWED_BACK.name());
            commissionRepository.save(c);
            log.info("Clawed back {} {} commission for family {} ({})",
                    c.getAmount(), c.getCurrency(), familyId, reason);
        });
    }

    private boolean isPaidPeriod(RevenueCatEvent.Event event) {
        var periodType = event.periodType();
        if (periodType != null && "TRIAL".equalsIgnoreCase(periodType)) {
            return false;
        }
        return event.price() != null && event.price() > 0;
    }

    private BigDecimal commissionAmount(RevenueCatEvent.Event event, AffiliateEntity affiliate) {
        var price = BigDecimal.valueOf(event.price());
        var takehome = event.takehomePercentage() != null
                ? BigDecimal.valueOf(event.takehomePercentage())
                : defaultTakehomeRatio;
        var net = price.multiply(takehome);
        var pct = affiliate.getCommissionPct().divide(BigDecimal.valueOf(100));
        return net.multiply(pct).setScale(2, RoundingMode.HALF_UP);
    }

    private String currencyOf(RevenueCatEvent.Event event) {
        var currency = event.currency();
        return currency == null || currency.isBlank() ? "SEK" : currency.toUpperCase(Locale.ROOT);
    }
}
