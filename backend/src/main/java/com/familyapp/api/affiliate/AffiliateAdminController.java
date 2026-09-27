package com.familyapp.api.affiliate;

import com.familyapp.api.admin.AdminAccess;
import com.familyapp.application.affiliate.AffiliatePayoutService;
import com.familyapp.application.affiliate.AffiliatePayoutService.PayoutResult;
import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.application.affiliate.AffiliateService.AffiliateAdminRow;
import com.familyapp.infrastructure.affiliate.AffiliatePayoutEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin view of the affiliate program: invite affiliates and see everyone's stats and
 * what is payable. Gated to the configured admin email(s) -- not every parent.
 */
@RestController
@RequestMapping("/api/v1/admin/affiliates")
public class AffiliateAdminController {

    private final AffiliateService affiliateService;
    private final AffiliatePayoutService payoutService;
    private final AdminAccess adminAccess;

    public AffiliateAdminController(
            AffiliateService affiliateService,
            AffiliatePayoutService payoutService,
            AdminAccess adminAccess) {
        this.affiliateService = affiliateService;
        this.payoutService = payoutService;
        this.adminAccess = adminAccess;
    }

    /** Invite a new affiliate. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliateAdminRow create(
            @RequestBody CreateAffiliateRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        adminAccess.requireAdmin(deviceToken);
        return affiliateService.createAffiliate(
                request == null ? null : request.name(),
                request == null ? null : request.email(),
                request == null ? null : request.referralCode());
    }

    /** All affiliates with referral counts and payable amounts. */
    @GetMapping
    public List<AffiliateAdminRow> list(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        adminAccess.requireAdmin(deviceToken);
        return affiliateService.listAffiliates();
    }

    /** Record a manual payout of everything payable for one affiliate (marks it PAID). */
    @PostMapping("/{id}/payout")
    public PayoutResult payOut(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) PayoutRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        adminAccess.requireAdmin(deviceToken);
        var method = request == null || request.method() == null ? "manual" : request.method();
        return payoutService.payOut(id, method);
    }

    /** Payout history for one affiliate. */
    @GetMapping("/{id}/payouts")
    public List<AffiliatePayoutEntity> payouts(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        adminAccess.requireAdmin(deviceToken);
        return payoutService.listPayouts(id);
    }

    public record CreateAffiliateRequest(String name, String email, String referralCode) {}

    public record PayoutRequest(String method) {}
}
