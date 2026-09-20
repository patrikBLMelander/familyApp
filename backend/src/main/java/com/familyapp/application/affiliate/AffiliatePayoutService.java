package com.familyapp.application.affiliate;

import com.familyapp.domain.affiliate.CommissionStatus;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionEntity;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliatePayoutEntity;
import com.familyapp.infrastructure.affiliate.AffiliatePayoutJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The payout side of the affiliate money model: promoting commissions past the refund
 * window to payable, and recording a manual payout.
 *
 * PENDING -> APPROVED happens automatically after a clearance window (the store refund
 * window), so "payable" always means "safe to pay -- no longer clawback-able".
 * APPROVED -> PAID is a manual act by the admin: pay by Swish/bank, then mark it paid,
 * which is what {@link #payOut} records.
 */
@Service
public class AffiliatePayoutService {

    private static final Logger log = LoggerFactory.getLogger(AffiliatePayoutService.class);

    private final AffiliateCommissionJpaRepository commissionRepository;
    private final AffiliatePayoutJpaRepository payoutRepository;
    private final int clearanceDays;

    public AffiliatePayoutService(
            AffiliateCommissionJpaRepository commissionRepository,
            AffiliatePayoutJpaRepository payoutRepository,
            @Value("${kidquest.affiliate.clearance-days:30}") int clearanceDays
    ) {
        this.commissionRepository = commissionRepository;
        this.payoutRepository = payoutRepository;
        this.clearanceDays = clearanceDays;
    }

    /**
     * Move commissions past the refund window from PENDING to APPROVED. Idempotent: a
     * second run finds nothing, so it is safe to run on more than one node.
     */
    @Transactional
    public int approveDueCommissions() {
        var cutoff = OffsetDateTime.now().minusDays(clearanceDays);
        var due = commissionRepository.findByStatusAndEarnedAtBefore(CommissionStatus.PENDING.name(), cutoff);
        due.forEach(c -> c.setStatus(CommissionStatus.APPROVED.name()));
        if (!due.isEmpty()) {
            commissionRepository.saveAll(due);
            log.info("Approved {} affiliate commissions past the {}-day clearance window", due.size(), clearanceDays);
        }
        return due.size();
    }

    /**
     * Record a manual payout of everything currently payable (APPROVED) for one affiliate:
     * create the payout row and mark those commissions PAID.
     */
    @Transactional
    public PayoutResult payOut(UUID affiliateId, String method) {
        var approved = commissionRepository.findByAffiliateIdAndStatus(
                affiliateId, CommissionStatus.APPROVED.name());
        if (approved.isEmpty()) {
            throw new IllegalArgumentException("Nothing payable for this affiliate");
        }
        var currencies = approved.stream().map(AffiliateCommissionEntity::getCurrency).distinct().toList();
        if (currencies.size() > 1) {
            // Would need one payout per currency; not worth the complexity until it happens.
            throw new IllegalStateException("Affiliate has payable commission in more than one currency");
        }
        var total = approved.stream()
                .map(AffiliateCommissionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var now = OffsetDateTime.now();

        var payout = new AffiliatePayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setAffiliateId(affiliateId);
        payout.setTotalAmount(total);
        payout.setCurrency(currencies.getFirst());
        payout.setStatus("PAID");
        payout.setMethod(method);
        payout.setPaidAt(now);
        payout.setCreatedAt(now);
        payoutRepository.save(payout);

        approved.forEach(c -> {
            c.setStatus(CommissionStatus.PAID.name());
            c.setPayoutId(payout.getId());
        });
        commissionRepository.saveAll(approved);

        log.info("Paid out {} {} to affiliate {} ({} commissions, {})",
                total, payout.getCurrency(), affiliateId, approved.size(), method);
        return new PayoutResult(payout.getId(), total, payout.getCurrency(), approved.size());
    }

    /** Payout history for one affiliate, newest first. */
    @Transactional(readOnly = true)
    public List<AffiliatePayoutEntity> listPayouts(UUID affiliateId) {
        return payoutRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId);
    }

    /** What a payout covered. */
    public record PayoutResult(UUID payoutId, BigDecimal total, String currency, int commissionCount) {}
}
