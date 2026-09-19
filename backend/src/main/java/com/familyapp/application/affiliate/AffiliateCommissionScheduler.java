package com.familyapp.application.affiliate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Once a day, promotes affiliate commissions past the refund window to APPROVED (payable).
 *
 * A separate bean rather than a method on the service, matching RecurringAllowanceScheduler:
 * the @Transactional boundary only applies across the Spring proxy. The work is idempotent
 * (a commission already APPROVED no longer matches the PENDING query), so a second run --
 * or a second node -- changes nothing, which is why this needs no distributed lock.
 */
@Component
public class AffiliateCommissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AffiliateCommissionScheduler.class);

    private final AffiliatePayoutService payoutService;

    public AffiliateCommissionScheduler(AffiliatePayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Stockholm")
    public void approveDueCommissions() {
        try {
            payoutService.approveDueCommissions();
        } catch (Exception e) {
            log.error("Affiliate commission approval run failed: {}", e.getMessage(), e);
        }
    }
}
