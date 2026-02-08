package com.familyapp.api.pet;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.pet.PetService;
import com.familyapp.application.pet.CollectedFoodService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.pet.ChildPet;
import com.familyapp.domain.pet.PetHistory;
import com.familyapp.domain.pet.CollectedFood;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    private final PetService petService;
    private final FamilyMemberService memberService;
    private final CollectedFoodService foodService;

    public PetController(PetService petService, FamilyMemberService memberService, CollectedFoodService foodService) {
        this.petService = petService;
        this.memberService = memberService;
        this.foodService = foodService;
    }

    @GetMapping("/current")
    public ResponseEntity<PetResponse> getCurrentPet(
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

        var pet = petService.getCurrentPet(memberId);
        if (pet.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(toResponse(pet.get()));
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

                // Children, assistants, and parents can select eggs
                if (member.role() != FamilyMember.Role.CHILD && member.role() != FamilyMember.Role.ASSISTANT && member.role() != FamilyMember.Role.PARENT) {
                    throw new IllegalArgumentException("Only children, assistants, and parents can select eggs");
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

    @PostMapping("/feed")
    public void feedPet(
            @RequestBody FeedPetRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                memberId = member.id();

                // Children, assistants, and parents (with pets enabled) can feed pets
                if (member.role() != FamilyMember.Role.CHILD && 
                    member.role() != FamilyMember.Role.ASSISTANT && 
                    member.role() != FamilyMember.Role.PARENT) {
                    throw new IllegalArgumentException("Only children, assistants, and parents can feed pets");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token: " + e.getMessage());
            }
        } else {
            throw new IllegalArgumentException("Device token is required");
        }

        // Validate request
        if (request.xpAmount() == null || request.xpAmount() <= 0) {
            throw new IllegalArgumentException("XP amount must be positive");
        }

        // Mark food as fed before awarding XP
        int actualFedAmount = foodService.markFoodAsFed(memberId, request.xpAmount());
        
        if (actualFedAmount == 0) {
            throw new IllegalArgumentException("No unfed food available");
        }
        
        // Award XP for the actual amount fed
        petService.feedPet(memberId, actualFedAmount);
    }

    @GetMapping("/collected-food")
    public CollectedFoodResponse getCollectedFood(
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

        var unfedFood = foodService.getUnfedFood(memberId);
        var totalCount = foodService.getUnfedFoodCount(memberId);

        return new CollectedFoodResponse(
                unfedFood.stream().map(this::toFoodResponse).toList(),
                totalCount
        );
    }

    @GetMapping("/last-fed-date")
    public LastFedDateResponse getLastFedDate(
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

        var lastFedAt = foodService.getLastFedAt(memberId);
        return new LastFedDateResponse(
                lastFedAt != null ? lastFedAt.toString() : null
        );
    }

    public record SelectEggRequest(String eggType, String name) {
    }

    public record FeedPetRequest(Integer xpAmount) {
    }

    public record CollectedFoodResponse(
            List<FoodItemResponse> foodItems,
            int totalCount
    ) {
    }

    public record FoodItemResponse(
            String id,
            String eventId, // null for bonus food
            int xpAmount,
            String collectedAt
    ) {
    }

    public record LastFedDateResponse(
            String lastFedAt // ISO 8601 string, or null if never fed
    ) {
    }

    private FoodItemResponse toFoodResponse(CollectedFood food) {
        return new FoodItemResponse(
                food.id().toString(),
                food.eventId() != null ? food.eventId().toString() : null,
                food.xpAmount(),
                food.collectedAt().toString()
        );
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

