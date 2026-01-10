package com.familyapp.api.pet;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.pet.PetService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.pet.ChildPet;
import com.familyapp.domain.pet.PetHistory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    private final PetService petService;
    private final FamilyMemberService memberService;

    public PetController(PetService petService, FamilyMemberService memberService) {
        this.petService = petService;
        this.memberService = memberService;
    }

    @GetMapping("/current")
    public PetResponse getCurrentPet(
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

        var pet = petService.getCurrentPet(memberId)
                .orElseThrow(() -> new IllegalArgumentException("No pet found for current month"));

        return toResponse(pet);
    }

    @PostMapping("/select-egg")
    @ResponseStatus(HttpStatus.CREATED)
    public PetResponse selectEgg(
            @RequestBody SelectEggRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                memberId = member.id();

                // Only children can select eggs
                if (member.role() != FamilyMember.Role.CHILD) {
                    throw new IllegalArgumentException("Only children can select eggs");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token: " + e.getMessage());
            }
        } else {
            throw new IllegalArgumentException("Device token is required");
        }

        // Validate request
        if (request.eggType() == null || request.eggType().isEmpty()) {
            throw new IllegalArgumentException("Egg type is required");
        }

        var pet = petService.selectEgg(memberId, request.eggType(), request.name());
        return toResponse(pet);
    }

    @GetMapping("/history")
    public List<PetHistoryResponse> getPetHistory(
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

        return petService.getPetHistory(memberId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @GetMapping("/available-eggs")
    public List<String> getAvailableEggTypes() {
        return petService.getAvailableEggTypes();
    }

    @GetMapping("/members/{memberId}/current")
    public PetResponse getMemberPet(
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

        var pet = petService.getCurrentPet(memberId).orElse(null);
        if (pet == null) {
            return null;
        }
        return toResponse(pet);
    }

    @GetMapping("/members/{memberId}/history")
    public List<PetHistoryResponse> getMemberPetHistory(
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

        return petService.getPetHistory(memberId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public record SelectEggRequest(String eggType, String name) {
    }

    private PetResponse toResponse(ChildPet pet) {
        return new PetResponse(
                pet.id(),
                pet.memberId(),
                pet.year(),
                pet.month(),
                pet.selectedEggType(),
                pet.petType(),
                pet.name(),
                pet.growthStage(),
                pet.hatchedAt(),
                pet.createdAt(),
                pet.updatedAt()
        );
    }

    private PetHistoryResponse toHistoryResponse(PetHistory history) {
        return new PetHistoryResponse(
                history.id(),
                history.memberId(),
                history.year(),
                history.month(),
                history.selectedEggType(),
                history.petType(),
                history.finalGrowthStage(),
                history.createdAt()
        );
    }

    public record PetResponse(
            UUID id,
            UUID memberId,
            int year,
            int month,
            String selectedEggType,
            String petType,
            String name,
            int growthStage,
            OffsetDateTime hatchedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PetHistoryResponse(
            UUID id,
            UUID memberId,
            int year,
            int month,
            String selectedEggType,
            String petType,
            int finalGrowthStage,
            OffsetDateTime createdAt
    ) {
    }
}

