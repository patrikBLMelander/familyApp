package com.familyapp.api.admin;

import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The gate for everything under /api/v1/admin: a parent's device token whose email is
 * listed in kidquest.affiliate.admin-emails. Shared by the affiliate admin and the
 * statistics page so both answer 401/403 the same way.
 */
@Component
public class AdminAccess {

    private final FamilyMemberService memberService;
    private final AffiliateService affiliateService;

    public AdminAccess(FamilyMemberService memberService, AffiliateService affiliateService) {
        this.memberService = memberService;
        this.affiliateService = affiliateService;
    }

    /** 401 without a valid device token, 403 when the member is not a configured admin. */
    public void requireAdmin(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Device token is required");
        }
        FamilyMember member;
        try {
            member = memberService.getMemberByDeviceToken(deviceToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token");
        }
        if (!affiliateService.isAdmin(member.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not an affiliate-program admin");
        }
    }
}
