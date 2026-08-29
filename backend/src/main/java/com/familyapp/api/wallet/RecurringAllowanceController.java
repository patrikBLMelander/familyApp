package com.familyapp.api.wallet;

import com.familyapp.application.allowance.RecurringAllowanceService;
import com.familyapp.application.allowance.RecurringAllowanceService.AllowanceSpec;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.subscription.EntitlementGuard;
import com.familyapp.domain.allowance.AllowanceKind;
import com.familyapp.infrastructure.allowance.RecurringAllowanceEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Automatisk peng för ett barn.
 *
 * Endast en förälder i samma familj, kontrollerat i tjänsten. Att raden är dold i
 * barnets vy är en designfråga; det här är spärren.
 *
 * Att sätta upp och ändra räknas som föräldraadministration och ligger därför bakom
 * entitlement-spärren, precis som att ge veckopeng för hand. Att LÄSA gör det inte --
 * en familj vars prenumeration löpt ut ska kunna se vad de en gång ställt in.
 */
@RestController
@RequestMapping("/api/v1/wallet/members/{memberId}/recurring-allowance")
public class RecurringAllowanceController {

    private final RecurringAllowanceService service;
    private final FamilyMemberService memberService;
    private final EntitlementGuard entitlementGuard;

    public RecurringAllowanceController(
            RecurringAllowanceService service,
            FamilyMemberService memberService,
            EntitlementGuard entitlementGuard
    ) {
        this.service = service;
        this.memberService = memberService;
        this.entitlementGuard = entitlementGuard;
    }

    @GetMapping
    public ResponseEntity<RecurringAllowanceResponse> get(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        var entity = service.get(memberId, requesterId(deviceToken));
        return entity == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(toResponse(entity));
    }

    @PutMapping
    public RecurringAllowanceResponse save(
            @PathVariable("memberId") UUID memberId,
            @RequestBody SaveRecurringAllowanceRequest body,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        entitlementGuard.requireEntitled(deviceToken);
        var spec = new AllowanceSpec(
                parseKind(body.kind()),
                body.amount(),
                body.weekday(),
                body.dayOfMonth(),
                body.level1(), body.level2(), body.level3(), body.level4(), body.level5()
        );
        return toResponse(service.save(memberId, requesterId(deviceToken), spec));
    }

    @DeleteMapping
    public ResponseEntity<Void> disable(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        entitlementGuard.requireEntitled(deviceToken);
        service.disable(memberId, requesterId(deviceToken));
        return ResponseEntity.noContent().build();
    }

    private AllowanceKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Välj vecko- eller månadspeng");
        }
        try {
            return AllowanceKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Okänd typ av peng: " + raw);
        }
    }

    private UUID requesterId(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        return memberService.getMemberByDeviceToken(deviceToken).id();
    }

    private RecurringAllowanceResponse toResponse(RecurringAllowanceEntity e) {
        return new RecurringAllowanceResponse(
                e.getMemberId(),
                e.getKind(),
                e.getAmount(),
                e.getWeekday(),
                e.getDayOfMonth(),
                e.getLevel1Amount(),
                e.getLevel2Amount(),
                e.getLevel3Amount(),
                e.getLevel4Amount(),
                e.getLevel5Amount(),
                e.isActive(),
                e.getNextDueOn()
        );
    }

    public record SaveRecurringAllowanceRequest(
            String kind,
            Integer amount,
            Integer weekday,
            Integer dayOfMonth,
            Integer level1,
            Integer level2,
            Integer level3,
            Integer level4,
            Integer level5
    ) {
    }

    public record RecurringAllowanceResponse(
            UUID memberId,
            String kind,
            Integer amount,
            Integer weekday,
            Integer dayOfMonth,
            Integer level1,
            Integer level2,
            Integer level3,
            Integer level4,
            Integer level5,
            boolean active,
            LocalDate nextDueOn
    ) {
    }
}
