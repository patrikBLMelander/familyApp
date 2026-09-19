package com.familyapp.api.affiliate;

import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.application.affiliate.AffiliateService.AffiliateSelf;
import com.familyapp.application.affiliate.AffiliateService.AffiliateSession;
import com.familyapp.application.affiliate.AffiliateService.AffiliateStats;
import com.familyapp.application.affiliate.AffiliateService.ReferralRow;
import java.util.List;
import com.familyapp.application.familymember.FamilyMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Affiliate-facing endpoints: a family redeeming a code, and the portal's own
 * activation / login / dashboard. The portal lives on the website under /affiliate.
 */
@RestController
@RequestMapping("/api/v1")
public class AffiliateController {

    private final AffiliateService affiliateService;
    private final FamilyMemberService memberService;

    public AffiliateController(AffiliateService affiliateService, FamilyMemberService memberService) {
        this.affiliateService = affiliateService;
        this.memberService = memberService;
    }

    /** A signed-in family member redeems an affiliate code for their family (first-touch). */
    @PostMapping("/affiliates/redeem")
    public ResponseEntity<Void> redeem(
            @RequestBody RedeemRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        var familyId = familyIdFor(deviceToken);
        affiliateService.redeem(familyId, request == null ? null : request.code());
        return ResponseEntity.noContent().build();
    }

    /** Invited affiliate sets a password. Succeeds only if an admin created the invite. */
    @PostMapping("/affiliate-auth/activate")
    public AffiliateSession activate(@RequestBody CredentialsRequest request) {
        return affiliateService.activate(request.email(), request.password());
    }

    /** Affiliate logs in; returns a portal token for X-Affiliate-Token. */
    @PostMapping("/affiliate-auth/login")
    public AffiliateSession login(@RequestBody CredentialsRequest request) {
        return affiliateService.login(request.email(), request.password());
    }

    /** The affiliate's own dashboard: code, link, referral count, earnings. */
    @GetMapping("/affiliate/me")
    public AffiliateSelf me(
            @RequestHeader(value = "X-Affiliate-Token", required = false) String token
    ) {
        var affiliate = affiliateService.authenticate(token);
        return affiliateService.selfView(affiliate);
    }

    /** The affiliate's analytics dashboard: totals, per-month time series, payout history. */
    @GetMapping("/affiliate/stats")
    public AffiliateStats stats(
            @RequestHeader(value = "X-Affiliate-Token", required = false) String token
    ) {
        var affiliate = affiliateService.authenticate(token);
        return affiliateService.statsView(affiliate);
    }

    /** The affiliate's referred families, anonymised: age, coarse status, still-earning flag. */
    @GetMapping("/affiliate/referrals")
    public List<ReferralRow> referrals(
            @RequestHeader(value = "X-Affiliate-Token", required = false) String token
    ) {
        var affiliate = affiliateService.authenticate(token);
        return affiliateService.referralsView(affiliate);
    }

    private UUID familyIdFor(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var member = memberService.getMemberByDeviceToken(deviceToken);
        if (member.familyId() == null) {
            throw new IllegalArgumentException("Member does not belong to a family");
        }
        return member.familyId();
    }

    public record RedeemRequest(String code) {}

    public record CredentialsRequest(String email, String password) {}
}
