package com.familyapp.api.xp;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.xp.XpService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.xp.MemberXpHistory;
import com.familyapp.domain.xp.MemberXpProgress;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/xp")
public class XpController {

    private final XpService xpService;
    private final FamilyMemberService memberService;

    public XpController(XpService xpService, FamilyMemberService memberService) {
        this.xpService = xpService;
        this.memberService = memberService;
    }

    @GetMapping("/current")
    public XpProgressResponse getCurrentProgress(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                memberId = member.id();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token");
            }
        } else {
            throw new IllegalArgumentException("Device token is required");
        }

        var progress = xpService.getCurrentProgress(memberId)
                .orElseThrow(() -> new IllegalArgumentException("No XP progress found for member"));

        return toResponse(progress);
    }

    @GetMapping("/history")
    public List<XpHistoryResponse> getHistory(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                memberId = member.id();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token");
            }
        } else {
            throw new IllegalArgumentException("Device token is required");
        }

        return xpService.getHistory(memberId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @GetMapping("/members/{memberId}/current")
    public XpProgressResponse getMemberCurrentProgress(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Verify the requester has access to this member's data (same family)
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var requester = memberService.getMemberByDeviceToken(deviceToken);
                var member = memberService.getMemberById(memberId);
                
                // Check if same family
                if (!requester.familyId().equals(member.familyId())) {
                    throw new IllegalArgumentException("Access denied");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token or access denied");
            }
        }

        var progress = xpService.getCurrentProgress(memberId)
                .orElseThrow(() -> new IllegalArgumentException("No XP progress found for member"));

        return toResponse(progress);
    }

    @GetMapping("/members/{memberId}/history")
    public List<XpHistoryResponse> getMemberHistory(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Verify the requester has access to this member's data (same family)
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var requester = memberService.getMemberByDeviceToken(deviceToken);
                var member = memberService.getMemberById(memberId);
                
                // Check if same family
                if (!requester.familyId().equals(member.familyId())) {
                    throw new IllegalArgumentException("Access denied");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token or access denied");
            }
        }

        return xpService.getHistory(memberId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /**
     * Award bonus XP to a child member (admin only - parents can give bonus to their children)
     */
    @PostMapping("/members/{memberId}/bonus")
    public XpProgressResponse awardBonusXp(
            @PathVariable("memberId") UUID memberId,
            @RequestBody AwardBonusXpRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Verify the requester is a parent in the same family
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }

        try {
            var requester = memberService.getMemberByDeviceToken(deviceToken);
            var member = memberService.getMemberById(memberId);
            
            // Check if same family
            if (!requester.familyId().equals(member.familyId())) {
                throw new IllegalArgumentException("Access denied");
            }

            // Only parents can award bonus XP
            if (requester.role() != FamilyMember.Role.PARENT) {
                throw new IllegalArgumentException("Only parents can award bonus XP");
            }

            // Only award to children
            if (member.role() != FamilyMember.Role.CHILD) {
                throw new IllegalArgumentException("Bonus XP can only be awarded to children");
            }

            // Validate XP amount (reasonable limit)
            if (request.xpPoints() <= 0 || request.xpPoints() > 100) {
                throw new IllegalArgumentException("XP points must be between 1 and 100");
            }

            // Award bonus XP
            xpService.awardBonusXp(memberId, request.xpPoints());

            // Return updated progress
            var progress = xpService.getCurrentProgress(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("No XP progress found for member"));

            return toResponse(progress);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid device token or access denied: " + e.getMessage());
        }
    }

    public record AwardBonusXpRequest(int xpPoints) {
    }

    private XpProgressResponse toResponse(MemberXpProgress progress) {
        return new XpProgressResponse(
                progress.id(),
                progress.memberId(),
                progress.year(),
                progress.month(),
                progress.currentXp(),
                progress.currentLevel(),
                progress.totalTasksCompleted(),
                progress.getXpForNextLevel(),
                progress.getXpInCurrentLevel()
        );
    }

    private XpHistoryResponse toHistoryResponse(MemberXpHistory history) {
        return new XpHistoryResponse(
                history.id(),
                history.memberId(),
                history.year(),
                history.month(),
                history.finalXp(),
                history.finalLevel(),
                history.totalTasksCompleted()
        );
    }

    public record XpProgressResponse(
            UUID id,
            UUID memberId,
            int year,
            int month,
            int currentXp,
            int currentLevel,
            int totalTasksCompleted,
            int xpForNextLevel,
            int xpInCurrentLevel
    ) {
    }

    public record XpHistoryResponse(
            UUID id,
            UUID memberId,
            int year,
            int month,
            int finalXp,
            int finalLevel,
            int totalTasksCompleted
    ) {
    }
}

