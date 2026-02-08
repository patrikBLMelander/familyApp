package com.familyapp.api.menstrualcycle;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.menstrualcycle.MenstrualCycleService;
import com.familyapp.application.menstrualcycle.MenstrualCycleService.CyclePrediction;
import com.familyapp.domain.menstrualcycle.MenstrualCycle;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/family-members/{memberId}/menstrual-cycle")
public class MenstrualCycleController {

    private final MenstrualCycleService service;
    private final FamilyMemberService familyMemberService;

    public MenstrualCycleController(
            MenstrualCycleService service,
            FamilyMemberService familyMemberService
    ) {
        this.service = service;
        this.familyMemberService = familyMemberService;
    }

    @GetMapping("/entries")
    public List<MenstrualCycleResponse> getEntries(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getRequesterId(deviceToken);
        var entries = service.getEntries(memberId, requesterId);
        return entries.stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public MenstrualCycleResponse createEntry(
            @PathVariable("memberId") UUID memberId,
            @RequestBody CreateEntryRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getRequesterId(deviceToken);
        var entry = service.createEntry(
                memberId,
                request.periodStartDate(),
                request.periodLength(),
                requesterId
        );
        return toResponse(entry);
    }

    @DeleteMapping("/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(
            @PathVariable("memberId") UUID memberId,
            @PathVariable("entryId") UUID entryId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getRequesterId(deviceToken);
        service.deleteEntry(entryId, requesterId);
    }

    @GetMapping("/prediction")
    public CyclePredictionResponse getPrediction(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = getRequesterId(deviceToken);
        var prediction = service.getPrediction(memberId, requesterId);
        return toPredictionResponse(prediction);
    }

    private UUID getRequesterId(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        try {
            var requester = familyMemberService.getMemberByDeviceToken(deviceToken);
            return requester.id();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid device token");
        }
    }

    private MenstrualCycleResponse toResponse(MenstrualCycle entry) {
        return new MenstrualCycleResponse(
                entry.id().toString(),
                entry.periodStartDate().toString(),
                entry.periodLength(),
                entry.cycleLength(),
                entry.createdAt().toString(),
                entry.updatedAt().toString()
        );
    }

    private CyclePredictionResponse toPredictionResponse(CyclePrediction prediction) {
        return new CyclePredictionResponse(
                prediction.nextPeriodStart().toString(),
                prediction.nextPeriodEnd().toString(),
                prediction.ovulationDate().toString(),
                prediction.fertileWindowStart().toString(),
                prediction.fertileWindowEnd().toString(),
                prediction.currentCycleDay(),
                prediction.currentPhase()
        );
    }

    public record CreateEntryRequest(
            LocalDate periodStartDate,
            Integer periodLength
    ) {
    }

    public record MenstrualCycleResponse(
            String id,
            String periodStartDate,
            Integer periodLength,
            Integer cycleLength,
            String createdAt,
            String updatedAt
    ) {
    }

    public record CyclePredictionResponse(
            String nextPeriodStart,
            String nextPeriodEnd,
            String ovulationDate,
            String fertileWindowStart,
            String fertileWindowEnd,
            int currentCycleDay,
            String currentPhase
    ) {
    }
}
