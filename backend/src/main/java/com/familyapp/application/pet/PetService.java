package com.familyapp.application.pet;

import com.familyapp.application.xp.XpService;
import com.familyapp.domain.pet.ChildPet;
import com.familyapp.domain.pet.PetHistory;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.ChildPetEntity;
import com.familyapp.infrastructure.pet.ChildPetJpaRepository;
import com.familyapp.infrastructure.pet.PetHistoryEntity;
import com.familyapp.infrastructure.pet.PetHistoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PetService {

    private final ChildPetJpaRepository petRepository;
    private final PetHistoryJpaRepository historyRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final XpService xpService;

    // Deterministic mapping: egg type -> pet type
    private static final Map<String, String> EGG_TO_PET_MAP = new HashMap<>();
    static {
        EGG_TO_PET_MAP.put("blue_egg", "dragon");
        EGG_TO_PET_MAP.put("green_egg", "cat");
        EGG_TO_PET_MAP.put("red_egg", "dog");
        EGG_TO_PET_MAP.put("yellow_egg", "bird");
        EGG_TO_PET_MAP.put("purple_egg", "rabbit");
        EGG_TO_PET_MAP.put("orange_egg", "bear");
        EGG_TO_PET_MAP.put("brown_egg", "snake");
        EGG_TO_PET_MAP.put("black_egg", "panda");
        EGG_TO_PET_MAP.put("gray_egg", "slot");
        EGG_TO_PET_MAP.put("teal_egg", "hydra");
        EGG_TO_PET_MAP.put("pink_egg", "unicorn");
        EGG_TO_PET_MAP.put("cyan_egg", "kapybara");
    }

    public PetService(
            ChildPetJpaRepository petRepository,
            PetHistoryJpaRepository historyRepository,
            FamilyMemberJpaRepository memberRepository,
            XpService xpService
    ) {
        this.petRepository = petRepository;
        this.historyRepository = historyRepository;
        this.memberRepository = memberRepository;
        this.xpService = xpService;
    }

    /**
     * Select an egg for the current month
     * Creates a pet record with the selected egg type
     * The pet type is determined deterministically from the egg type
     */
    public ChildPet selectEgg(UUID memberId, String eggType, String name) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Only children can have pets
        if (!"CHILD".equals(member.getRole())) {
            throw new IllegalArgumentException("Only children can select pets");
        }

        // Validate egg type
        if (!EGG_TO_PET_MAP.containsKey(eggType)) {
            throw new IllegalArgumentException("Invalid egg type: " + eggType);
        }

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // Check if pet already exists for this month
        var existingPet = petRepository.findByMemberIdAndYearAndMonth(memberId, year, month);
        if (existingPet.isPresent()) {
            throw new IllegalArgumentException("Pet already selected for this month");
        }

        // Determine pet type from egg type
        String petType = EGG_TO_PET_MAP.get(eggType);

        // Validate name (optional but if provided, should be reasonable length)
        if (name != null && name.length() > 100) {
            throw new IllegalArgumentException("Pet name must be 100 characters or less");
        }

        // Create new pet entity
        var petEntity = new ChildPetEntity();
        petEntity.setId(UUID.randomUUID());
        petEntity.setMember(member);
        petEntity.setYear(year);
        petEntity.setMonth(month);
        petEntity.setSelectedEggType(eggType);
        petEntity.setPetType(petType);
        petEntity.setName(name); // Can be null
        petEntity.setGrowthStage(1); // Start at stage 1
        petEntity.setHatchedAt(OffsetDateTime.now()); // Hatch immediately when selected
        petEntity.setCreatedAt(OffsetDateTime.now());
        petEntity.setUpdatedAt(OffsetDateTime.now());

        var saved = petRepository.save(petEntity);
        return toDomain(saved);
    }

    /**
     * Get current month's pet for a member
     * Also updates growth stage based on current XP level
     */
    @Transactional
    public Optional<ChildPet> getCurrentPet(UUID memberId) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        return petRepository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .map(entity -> {
                    // Update growth stage based on current XP level
                    var progressOpt = xpService.getCurrentProgress(memberId);
                    if (progressOpt.isPresent()) {
                        int level = progressOpt.get().currentLevel();
                        int newGrowthStage = calculateGrowthStage(level);
                        
                        // Only update if growth stage changed
                        if (entity.getGrowthStage() != newGrowthStage) {
                            entity.setGrowthStage(newGrowthStage);
                            entity.setUpdatedAt(OffsetDateTime.now());
                            petRepository.save(entity);
                        }
                    }
                    return toDomain(entity);
                });
    }

    /**
     * Get pet history for a member
     */
    @Transactional(readOnly = true)
    public List<PetHistory> getPetHistory(UUID memberId) {
        return historyRepository.findByMemberIdOrderByYearDescMonthDesc(memberId).stream()
                .map(this::toHistoryDomain)
                .toList();
    }

    /**
     * Calculate growth stage from XP level
     * Level 1 = Stage 1
     * Level 2 = Stage 2
     * Level 3 = Stage 3
     * Level 4 = Stage 4
     * Level 5 = Stage 5
     */
    public int calculateGrowthStage(int level) {
        // Level 1-5 maps directly to growth stage 1-5
        return Math.min(Math.max(level, ChildPet.MIN_GROWTH_STAGE), ChildPet.MAX_GROWTH_STAGE);
    }

    /**
     * Update growth stage for a pet based on current XP level
     * Called when XP changes
     */
    public void updateGrowthStage(UUID memberId) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        var petEntityOpt = petRepository.findByMemberIdAndYearAndMonth(memberId, year, month);
        if (petEntityOpt.isEmpty()) {
            return; // No pet to update
        }

        var progressOpt = xpService.getCurrentProgress(memberId);
        if (progressOpt.isEmpty()) {
            return; // No XP progress to base growth stage on
        }

        var petEntity = petEntityOpt.get();
        int level = progressOpt.get().currentLevel();
        int newGrowthStage = calculateGrowthStage(level);

        if (petEntity.getGrowthStage() != newGrowthStage) {
            petEntity.setGrowthStage(newGrowthStage);
            petEntity.setUpdatedAt(OffsetDateTime.now());
            petRepository.save(petEntity);
        }
    }

    /**
     * Get valid egg types
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableEggTypes() {
        return EGG_TO_PET_MAP.keySet().stream().sorted().toList();
    }

    private ChildPet toDomain(ChildPetEntity entity) {
        return new ChildPet(
                entity.getId(),
                entity.getMember().getId(),
                entity.getYear(),
                entity.getMonth(),
                entity.getSelectedEggType(),
                entity.getPetType(),
                entity.getName(),
                entity.getGrowthStage(),
                entity.getHatchedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PetHistory toHistoryDomain(PetHistoryEntity entity) {
        return new PetHistory(
                entity.getId(),
                entity.getMember().getId(),
                entity.getYear(),
                entity.getMonth(),
                entity.getSelectedEggType(),
                entity.getPetType(),
                entity.getFinalGrowthStage(),
                entity.getCreatedAt()
        );
    }
}

