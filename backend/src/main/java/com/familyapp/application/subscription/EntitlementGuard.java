package com.familyapp.application.subscription;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.subscription.SubscriptionRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The one place that refuses a request because a family has not paid.
 *
 * What it guards is deliberately narrow: adding and changing chores, adding and
 * removing family members, and managing the wallet. In other words a parent's
 * administration.
 *
 * What it must never guard is anything a child does. Completing a chore, feeding a
 * pet, choosing an egg, spending their own pocket money -- all of that keeps working
 * when a trial runs out, because a child should not lose their animal over an adult's
 * unpaid bill. Reads are never guarded either, and neither are password or e-mail
 * changes: those are how someone gets back into their account, and locking them behind
 * a payment they cannot reach the settings to make would be a trap.
 *
 * The gate is applied per endpoint rather than per role. Role would be the obvious
 * implementation and it is wrong: a parent tapping a chore complete inside a child's
 * view is participating, not administering, and would be blocked by a rule that only
 * looked at who was calling.
 */
@Service
public class EntitlementGuard {

    private static final Logger log = LoggerFactory.getLogger(EntitlementGuard.class);

    private final FamilyMemberService memberService;
    private final SubscriptionService subscriptionService;

    public EntitlementGuard(FamilyMemberService memberService, SubscriptionService subscriptionService) {
        this.memberService = memberService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * @throws SubscriptionRequiredException if the caller's family is no longer entitled
     */
    public void requireEntitled(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            // Not this guard's job. The endpoint's own authorisation check answers
            // that, and it must not be possible to skip one by failing the other.
            return;
        }
        var member = memberService.getMemberByDeviceToken(deviceToken);
        var familyId = member.familyId();
        if (familyId == null) {
            // A member belonging to no family has nothing to bill and nothing to
            // protect. Refusing here would lock out a row that predates families.
            return;
        }
        if (!subscriptionService.isEntitled(familyId)) {
            log.info("Family {} blocked from a paid action: subscription not active", familyId);
            throw new SubscriptionRequiredException(
                    "Subscription required for this action"
            );
        }
    }
}
