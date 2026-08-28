package com.familyapp.api.chore;

import com.familyapp.application.subscription.EntitlementGuard;
import com.familyapp.application.chore.DailyChoreService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.chore.DailyChore;
import com.familyapp.domain.chore.DailyChoreCompletion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-chores")
public class DailyChoreController {

    private final DailyChoreService choreService;
    private final FamilyMemberService memberService;
    private final EntitlementGuard entitlementGuard;

    public DailyChoreController(
            DailyChoreService choreService,
            FamilyMemberService memberService,
            EntitlementGuard entitlementGuard
    ) {
        this.choreService = choreService;
        this.memberService = memberService;
        this.entitlementGuard = entitlementGuard;
    }

    @GetMapping("/members/{memberId}")
    public List<DailyChoreResponse> getChoresForMember(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getMemberIdFromToken(deviceToken);
        return choreService.getChoresForMember(requesterId, memberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/members/{memberId}/for-date")
    public List<DailyChoreWithCompletionResponse> getChoresForDate(
            @PathVariable("memberId") UUID memberId,
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getMemberIdFromToken(deviceToken);
        return choreService.getChoresForDate(requesterId, memberId, date)
                .stream()
                .map(this::toWithCompletionResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyChoreResponse createChore(
            @RequestBody CreateDailyChoreRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        entitlementGuard.requireEntitled(deviceToken);
        UUID requesterId = getMemberIdFromToken(deviceToken);
        DailyChore chore = choreService.createChore(
                requesterId, request.memberId(), request.title(), request.weekdays(), request.xpPoints()
        );
        return toResponse(chore);
    }

    @DeleteMapping("/{choreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChore(
            @PathVariable("choreId") UUID choreId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        entitlementGuard.requireEntitled(deviceToken);
        UUID requesterId = getMemberIdFromToken(deviceToken);
        choreService.deleteChore(requesterId, choreId);
    }

    @PostMapping("/{choreId}/completion")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyChoreCompletionResponse markCompleted(
            @PathVariable("choreId") UUID choreId,
            @RequestBody MarkChoreCompletedRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getMemberIdFromToken(deviceToken);
        DailyChoreCompletion completion = choreService.markCompleted(requesterId, choreId, request.date());
        return toCompletionResponse(completion);
    }

    @DeleteMapping("/{choreId}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmarkCompleted(
            @PathVariable("choreId") UUID choreId,
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getMemberIdFromToken(deviceToken);
        choreService.unmarkCompleted(requesterId, choreId, date);
    }

    // --- Request / Response records ---

    public record CreateDailyChoreRequest(
            UUID memberId,
            String title,
            List<String> weekdays,
            int xpPoints
    ) {}

    public record MarkChoreCompletedRequest(
            LocalDate date
    ) {}

    public record DailyChoreResponse(
            String id,
            String memberId,
            String title,
            List<String> weekdays,
            int xpPoints,
            boolean isActive,
            String createdAt
    ) {}

    public record DailyChoreWithCompletionResponse(
            DailyChoreResponse chore,
            boolean completed,
            String completionId
    ) {}

    public record DailyChoreCompletionResponse(
            String id,
            String choreId,
            String memberId,
            String occurrenceDate,
            String completedAt
    ) {}

    // --- Private helpers ---

    private UUID getMemberIdFromToken(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        try {
            var member = memberService.getMemberByDeviceToken(deviceToken);
            return member.id();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid device token");
        }
    }

    private DailyChoreResponse toResponse(DailyChore c) {
        return new DailyChoreResponse(
                c.id().toString(),
                c.memberId().toString(),
                c.title(),
                c.weekdays(),
                c.xpPoints(),
                c.isActive(),
                c.createdAt().toString()
        );
    }

    private DailyChoreWithCompletionResponse toWithCompletionResponse(DailyChoreService.DailyChoreWithCompletion c) {
        return new DailyChoreWithCompletionResponse(
                toResponse(c.chore()),
                c.completed(),
                c.completionId()
        );
    }

    private DailyChoreCompletionResponse toCompletionResponse(DailyChoreCompletion c) {
        return new DailyChoreCompletionResponse(
                c.id().toString(),
                c.choreId().toString(),
                c.memberId().toString(),
                c.occurrenceDate().toString(),
                c.completedAt().toString()
        );
    }
}
