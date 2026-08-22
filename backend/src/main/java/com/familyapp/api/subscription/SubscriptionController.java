package com.familyapp.api.subscription;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.subscription.SubscriptionService;
import com.familyapp.domain.subscription.SubscriptionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final FamilyMemberService memberService;

    public SubscriptionController(SubscriptionService subscriptionService, FamilyMemberService memberService) {
        this.subscriptionService = subscriptionService;
        this.memberService = memberService;
    }

    /**
     * The client's whole view of billing: am I allowed in, and what should I say
     * about it. Any member of the family may read it, children included -- their
     * app needs to know it has been locked out just as much as a parent's does.
     */
    @GetMapping("/status")
    public SubscriptionStatusResponse getStatus(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var member = memberService.getMemberByDeviceToken(deviceToken);
        var familyId = member.familyId();
        if (familyId == null) {
            throw new IllegalArgumentException("Member does not belong to a family");
        }

        var subscription = subscriptionService.getOrCreate(familyId);
        var daysLeft = subscriptionService.trialDaysRemaining(subscription);

        return new SubscriptionStatusResponse(
                subscription.status(),
                subscription.status().isEntitled(),
                subscription.trialEndsAt(),
                daysLeft,
                subscription.status() == SubscriptionStatus.TRIAL,
                subscription.currentPeriodEnd(),
                subscription.platform(),
                subscription.cancelAtPeriodEnd(),
                subscription.status() == SubscriptionStatus.COMPED
        );
    }

    /**
     * @param entitled the only field a client should gate on. The rest is for what
     *                 to show, not what to allow -- and the server enforces the
     *                 same answer on every write regardless.
     */
    public record SubscriptionStatusResponse(
            SubscriptionStatus status,
            boolean entitled,
            OffsetDateTime trialEndsAt,
            long trialDaysRemaining,
            boolean inTrial,
            OffsetDateTime currentPeriodEnd,
            String platform,
            boolean cancelAtPeriodEnd,
            /** Free access granted by hand. Worth showing so nobody waits for a charge. */
            boolean comped
    ) {
    }
}
