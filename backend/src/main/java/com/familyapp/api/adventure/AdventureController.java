package com.familyapp.api.adventure;

import com.familyapp.application.adventure.AdventureService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.adventure.LootResult;
import com.familyapp.infrastructure.adventure.AdventureEntity;
import com.familyapp.infrastructure.adventure.AdventureJpaRepository;
import com.familyapp.infrastructure.adventure.ChildInventoryEntity;
import com.familyapp.infrastructure.adventure.ChildInventoryJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Adventures. Auth follows the rest of the app: the X-Device-Token header identifies the
 * member. The countdown is computed here from the stored start time and duration, never
 * taken from the client.
 */
@RestController
@RequestMapping("/api/v1/adventures")
public class AdventureController {

    private final AdventureService adventureService;
    private final FamilyMemberService memberService;
    private final AdventureJpaRepository adventureRepository;
    private final ChildInventoryJpaRepository inventoryRepository;

    public AdventureController(
            AdventureService adventureService,
            FamilyMemberService memberService,
            AdventureJpaRepository adventureRepository,
            ChildInventoryJpaRepository inventoryRepository
    ) {
        this.adventureService = adventureService;
        this.memberService = memberService;
        this.adventureRepository = adventureRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping
    public AdventureStateResponse getState(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = requireMember(deviceToken);
        var adventures = adventureRepository.findByMemberIdOrderByStartedAtDesc(memberId).stream()
                .map(AdventureController::toResponse)
                .toList();
        return new AdventureStateResponse(adventureService.ticketBalance(memberId), adventures);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdventureResponse start(
            @RequestBody StartAdventureRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = requireMember(deviceToken);
        if (request.scene() == null || request.scene().isEmpty()) {
            throw new IllegalArgumentException("Scene is required");
        }
        return toResponse(adventureService.start(memberId, request.scene()));
    }

    @PostMapping("/{id}/claim")
    public LootResponse claim(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireMember(deviceToken);
        LootResult loot = adventureService.claim(id);
        return new LootResponse(loot.type().name(), loot.ref(), loot.quantity());
    }

    @GetMapping("/inventory")
    public List<InventoryItemResponse> getInventory(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = requireMember(deviceToken);
        return inventoryRepository.findByMemberId(memberId).stream()
                .map(AdventureController::toInventoryResponse)
                .toList();
    }

    // --- Member-scoped, for a parent acting in a child's view (parity with the picker). ---

    @GetMapping("/members/{memberId}")
    public AdventureStateResponse getStateForMember(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireParentOf(deviceToken, memberId);
        var adventures = adventureRepository.findByMemberIdOrderByStartedAtDesc(memberId).stream()
                .map(AdventureController::toResponse)
                .toList();
        return new AdventureStateResponse(adventureService.ticketBalance(memberId), adventures);
    }

    @PostMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AdventureResponse startForMember(
            @PathVariable("memberId") UUID memberId,
            @RequestBody StartAdventureRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireParentOf(deviceToken, memberId);
        if (request.scene() == null || request.scene().isEmpty()) {
            throw new IllegalArgumentException("Scene is required");
        }
        return toResponse(adventureService.start(memberId, request.scene()));
    }

    @PostMapping("/members/{memberId}/{id}/claim")
    public LootResponse claimForMember(
            @PathVariable("memberId") UUID memberId,
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireParentOf(deviceToken, memberId);
        var adventure = adventureRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Adventure not found: " + id));
        if (!adventure.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("Access denied");
        }
        LootResult loot = adventureService.claim(id);
        return new LootResponse(loot.type().name(), loot.ref(), loot.quantity());
    }

    @GetMapping("/members/{memberId}/inventory")
    public List<InventoryItemResponse> getInventoryForMember(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireParentOf(deviceToken, memberId);
        return inventoryRepository.findByMemberId(memberId).stream()
                .map(AdventureController::toInventoryResponse)
                .toList();
    }

    private UUID requireMember(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        return memberService.getMemberByDeviceToken(deviceToken).id();
    }

    /** Authorises a parent acting for a child in the same family (mirrors PetController). */
    private void requireParentOf(String deviceToken, UUID memberId) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var requester = memberService.getMemberByDeviceToken(deviceToken);
        var member = memberService.getMemberById(memberId);
        if (requester.familyId() == null || !requester.familyId().equals(member.familyId())) {
            throw new IllegalArgumentException("Access denied");
        }
        if (requester.role() != com.familyapp.domain.familymember.FamilyMember.Role.PARENT) {
            throw new IllegalArgumentException("Only a parent can act for another member");
        }
    }

    private static AdventureResponse toResponse(AdventureEntity a) {
        var readyAt = a.getStartedAt().plusSeconds(a.getDurationSecs());
        long secondsRemaining = Math.max(0, Duration.between(OffsetDateTime.now(), readyAt).getSeconds());
        boolean ready = secondsRemaining == 0 && !"CLAIMED".equals(a.getStatus());
        return new AdventureResponse(
                a.getId(),
                a.getScene(),
                a.getStatus(),
                a.getDurationSecs(),
                secondsRemaining,
                ready,
                a.getLootType(),
                a.getLootRef(),
                a.getLootQty(),
                a.getStartedAt()
        );
    }

    private static InventoryItemResponse toInventoryResponse(ChildInventoryEntity i) {
        return new InventoryItemResponse(i.getItemId(), i.getAcquiredAt());
    }

    // ---- DTOs ----

    public record StartAdventureRequest(String scene) {
    }

    public record AdventureStateResponse(long ticketBalance, List<AdventureResponse> adventures) {
    }

    public record AdventureResponse(
            UUID id,
            String scene,
            String status,
            int durationSecs,
            long secondsRemaining,
            boolean ready,
            String lootType,
            String lootRef,
            Integer lootQty,
            OffsetDateTime startedAt
    ) {
    }

    public record LootResponse(String type, String ref, int quantity) {
    }

    public record InventoryItemResponse(String itemId, OffsetDateTime acquiredAt) {
    }
}
