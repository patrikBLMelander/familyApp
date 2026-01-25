package com.familyapp.application.pet;

import com.familyapp.domain.pet.CollectedFood;
import com.familyapp.infrastructure.calendar.CalendarEventJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.CollectedFoodEntity;
import com.familyapp.infrastructure.pet.CollectedFoodJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CollectedFoodService {

    private final CollectedFoodJpaRepository foodRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final CalendarEventJpaRepository eventRepository;

    public CollectedFoodService(
            CollectedFoodJpaRepository foodRepository,
            FamilyMemberJpaRepository memberRepository,
            CalendarEventJpaRepository eventRepository
    ) {
        this.foodRepository = foodRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Add bonus food (from parent giving bonus XP)
     * Creates one food item per XP point without an associated event
     * Note: This requires event_id to be nullable in the database
     */
    public void addBonusFood(UUID memberId, int xpPoints) {
        if (xpPoints <= 0) {
            return; // No food to add
        }

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Create one food item per XP point (batch insert for performance)
        // For bonus food, event_id will be null (requires nullable column)
        var now = OffsetDateTime.now();
        var foodEntities = new ArrayList<CollectedFoodEntity>(xpPoints);
        for (int i = 0; i < xpPoints; i++) {
            var foodEntity = new CollectedFoodEntity();
            foodEntity.setId(UUID.randomUUID());
            foodEntity.setMember(member);
            foodEntity.setEvent(null); // Bonus food has no associated event
            foodEntity.setXpAmount(1);
            foodEntity.setFed(false);
            foodEntity.setCollectedAt(now);
            foodEntity.setFedAt(null);
            foodEntities.add(foodEntity);
        }
        foodRepository.saveAll(foodEntities);
    }

    /**
     * Add food when a task is completed
     * Creates one food item per XP point
     */
    public void addFoodFromTask(UUID memberId, UUID eventId, int xpPoints) {
        if (xpPoints <= 0) {
            return; // No food to add
        }

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));

        // Create one food item per XP point (batch insert for performance)
        var now = OffsetDateTime.now();
        var foodEntities = new ArrayList<CollectedFoodEntity>(xpPoints);
        for (int i = 0; i < xpPoints; i++) {
            var foodEntity = new CollectedFoodEntity();
            foodEntity.setId(UUID.randomUUID());
            foodEntity.setMember(member);
            foodEntity.setEvent(event);
            foodEntity.setXpAmount(1);
            foodEntity.setFed(false);
            foodEntity.setCollectedAt(now);
            foodEntity.setFedAt(null);
            foodEntities.add(foodEntity);
        }
        foodRepository.saveAll(foodEntities);
    }

    /**
     * Remove food when a task is uncompleted
     * Only removes unfed food for this specific event
     * Returns the number of food items removed
     */
    public int removeFoodFromTask(UUID memberId, UUID eventId, int xpPoints) {
        if (xpPoints <= 0) {
            return 0;
        }

        // Check total unfed food first - user can use food from any task to "pay back" XP
        int totalUnfedFood = foodRepository.countUnfedFoodXpByMemberId(memberId);
        
        if (totalUnfedFood < xpPoints) {
            throw new IllegalArgumentException(
                    String.format("Kan inte avmarkera syssla: Du har inte tillräckligt med omatad mat. " +
                            "Du behöver %d omatad mat, men har bara %d omatad mat totalt.", 
                            xpPoints, totalUnfedFood)
            );
        }

        // Get all unfed food (from any task) ordered by oldest first
        var allUnfedFood = foodRepository.findUnfedFoodByMemberId(memberId);

        // Remove the required amount (oldest first, across all tasks)
        int remainingToRemove = xpPoints;
        int removed = 0;
        var toDelete = new ArrayList<CollectedFoodEntity>();
        
        for (var food : allUnfedFood) {
            if (remainingToRemove <= 0) {
                break;
            }
            
            int foodXp = food.getXpAmount();
            toDelete.add(food);
            remainingToRemove -= foodXp;
            removed += foodXp;
        }

        if (!toDelete.isEmpty()) {
            foodRepository.deleteAll(toDelete);
        }

        return removed;
    }

    /**
     * Get all unfed food for a member
     */
    @Transactional(readOnly = true)
    public List<CollectedFood> getUnfedFood(UUID memberId) {
        return foodRepository.findUnfedFoodByMemberId(memberId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get total unfed food count (XP amount) for a member
     */
    @Transactional(readOnly = true)
    public int getUnfedFoodCount(UUID memberId) {
        return foodRepository.countUnfedFoodXpByMemberId(memberId);
    }

    /**
     * Mark food as fed (up to specified amount)
     * Returns the actual amount of food that was fed
     */
    public int markFoodAsFed(UUID memberId, int amount) {
        if (amount <= 0) {
            return 0;
        }

        var unfedFood = foodRepository.findUnfedFoodByMemberId(memberId);
        if (unfedFood.isEmpty()) {
            return 0;
        }

        int fed = 0;
        var now = OffsetDateTime.now();
        var toUpdate = new ArrayList<CollectedFoodEntity>();

        for (var food : unfedFood) {
            if (fed >= amount) {
                break;
            }

            food.setFed(true);
            food.setFedAt(now);
            toUpdate.add(food);
            fed += food.getXpAmount();
        }

        if (!toUpdate.isEmpty()) {
            foodRepository.saveAll(toUpdate);
        }

        return fed;
    }

    /**
     * Check if member has enough unfed food for a specific event
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughUnfedFood(UUID memberId, UUID eventId, int requiredXp) {
        var unfedFood = foodRepository.findUnfedFoodByMemberIdAndEventId(memberId, eventId);
        int totalXp = unfedFood.stream().mapToInt(CollectedFoodEntity::getXpAmount).sum();
        return totalXp >= requiredXp;
    }

    private CollectedFood toDomain(CollectedFoodEntity entity) {
        return new CollectedFood(
                entity.getId(),
                entity.getMember().getId(),
                entity.getEvent() != null ? entity.getEvent().getId() : null,
                entity.getXpAmount(),
                entity.isFed(),
                entity.getCollectedAt(),
                entity.getFedAt()
        );
    }
}
