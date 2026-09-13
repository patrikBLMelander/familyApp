package com.familyapp.application.pet;

import com.familyapp.application.adventure.AdventureService;
import com.familyapp.application.adventure.EggCatalog;
import com.familyapp.application.xp.XpService;
import com.familyapp.domain.pet.ChildPet;
import com.familyapp.domain.pet.PetHistory;
import com.familyapp.infrastructure.adventure.ChildEggUnlockJpaRepository;
import com.familyapp.infrastructure.adventure.ChildInventoryJpaRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PetService {

    private final ChildPetJpaRepository petRepository;
    private final PetHistoryJpaRepository historyRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final XpService xpService;
    private final ChildEggUnlockJpaRepository eggUnlockRepository;
    private final ChildInventoryJpaRepository inventoryRepository;
    private final AdventureService adventureService;

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
        EGG_TO_PET_MAP.put("white_egg", "shark");
        EGG_TO_PET_MAP.put("golden_egg", "lion");
    }

    public PetService(
            ChildPetJpaRepository petRepository,
            PetHistoryJpaRepository historyRepository,
            FamilyMemberJpaRepository memberRepository,
            XpService xpService,
            ChildEggUnlockJpaRepository eggUnlockRepository,
            ChildInventoryJpaRepository inventoryRepository,
            AdventureService adventureService
    ) {
        this.petRepository = petRepository;
        this.historyRepository = historyRepository;
        this.memberRepository = memberRepository;
        this.xpService = xpService;
        this.eggUnlockRepository = eggUnlockRepository;
        this.inventoryRepository = inventoryRepository;
        this.adventureService = adventureService;
    }

    /**
     * Select an egg for the current month
     * Creates a pet record with the selected egg type
     * The pet type is determined deterministically from the egg type
     */
    public ChildPet selectEgg(UUID memberId, String eggType, String name) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Children, assistants, and parents can have pets
        String role = member.getRole();
        if (!"CHILD".equals(role) && !"ASSISTANT".equals(role) && !"PARENT".equals(role)) {
            throw new IllegalArgumentException("Only children, assistants, and parents can select pets");
        }

        // Validate egg type
        if (!EGG_TO_PET_MAP.containsKey(eggType)) {
            throw new IllegalArgumentException("Invalid egg type: " + eggType);
        }

        // The gate: only unlocked eggs can be picked. Commons are seeded lazily first so a
        // brand-new child is never wrongly rejected for one of the four they start with.
        adventureService.ensureCommonsUnlocked(memberId);
        if (!eggUnlockRepository.existsByMemberAndEggType(memberId, eggType)) {
            throw new IllegalArgumentException("Egg not unlocked: " + eggType);
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

    /**
     * Every egg with its rarity and whether this child has unlocked and already collected
     * it -- the data the three-zone picker needs (selectable / to discover / collected).
     * Commons are ensured first so a fresh child always has its four.
     *
     * NOT read-only: ensureCommonsUnlocked lazily seeds the four commons, and a read-only
     * transaction suppresses those inserts (Hibernate never flushes), which left every
     * child created after the V48 migration with no selectable egg at all.
     */
    @Transactional
    public List<EggOption> getEggOptions(UUID memberId) {
        adventureService.ensureCommonsUnlocked(memberId);
        var unlocked = new HashSet<>(eggUnlockRepository.findEggTypesByMemberId(memberId));
        Set<String> collected = historyRepository.findByMemberIdOrderByYearDescMonthDesc(memberId).stream()
                .map(PetHistoryEntity::getSelectedEggType)
                .collect(Collectors.toSet());
        return EGG_TO_PET_MAP.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new EggOption(
                        entry.getKey(),
                        entry.getValue(),
                        EggCatalog.rarityOf(entry.getKey()).name(),
                        unlocked.contains(entry.getKey()),
                        collected.contains(entry.getKey())))
                .toList();
    }

    /**
     * Put a frame on this month's scene, or clear it with null. The child must own the
     * frame (won on an adventure); an unowned frame is rejected.
     */
    public ChildPet equipFrame(UUID memberId, String frameId) {
        LocalDate now = LocalDate.now();
        var petEntity = petRepository.findByMemberIdAndYearAndMonth(memberId, now.getYear(), now.getMonthValue())
                .orElseThrow(() -> new IllegalArgumentException("No pet this month"));
        if (frameId != null && !inventoryRepository.existsByMemberAndItem(memberId, frameId)) {
            throw new IllegalArgumentException("Frame not owned: " + frameId);
        }
        petEntity.setEquippedFrame(frameId);
        petEntity.setUpdatedAt(OffsetDateTime.now());
        return toDomain(petRepository.save(petEntity));
    }

    /**
     * Put a scene decoration on this month's scene, or clear it with null. The child must
     * own the item (won on an adventure); an unowned item is rejected.
     */
    public ChildPet equipSceneItem(UUID memberId, String itemId) {
        LocalDate now = LocalDate.now();
        var petEntity = petRepository.findByMemberIdAndYearAndMonth(memberId, now.getYear(), now.getMonthValue())
                .orElseThrow(() -> new IllegalArgumentException("No pet this month"));
        if (itemId != null && !inventoryRepository.existsByMemberAndItem(memberId, itemId)) {
            throw new IllegalArgumentException("Scene item not owned: " + itemId);
        }
        petEntity.setEquippedSceneItem(itemId);
        petEntity.setUpdatedAt(OffsetDateTime.now());
        return toDomain(petRepository.save(petEntity));
    }

    /** One row of the egg picker: the egg, the animal it hatches, its rarity, and this
     *  child's relationship to it. */
    public record EggOption(String eggType, String petType, String rarity, boolean unlocked, boolean collected) {
    }

    /**
     * Feed the pet - awards XP to the member
     * This is called when the child feeds their pet with collected food
     * @param memberId The member ID
     * @param xpAmount The amount of XP to award (number of food items)
     */
    public void feedPet(UUID memberId, int xpAmount) {
        if (xpAmount <= 0) {
            throw new IllegalArgumentException("XP amount must be positive");
        }

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Children, assistants, and parents can feed pets
        String role = member.getRole();
        if (!"CHILD".equals(role) && !"ASSISTANT".equals(role) && !"PARENT".equals(role)) {
            throw new IllegalArgumentException("Only children, assistants, and parents can feed pets");
        }

        // Award XP
        xpService.awardXp(memberId, xpAmount);

        // Update growth stage based on new XP level
        updateGrowthStage(memberId);
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
                entity.getUpdatedAt(),
                entity.getEquippedFrame(),
                entity.getEquippedSceneItem()
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
                entity.getCreatedAt(),
                entity.getFrame()
        );
    }
}

