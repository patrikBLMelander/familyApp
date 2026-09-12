package com.familyapp.application.adventure;

import com.familyapp.application.pet.CollectedFoodService;
import com.familyapp.domain.adventure.EggRarity;
import com.familyapp.domain.adventure.LootResult;
import com.familyapp.domain.adventure.LootType;
import com.familyapp.infrastructure.adventure.AdventureEntity;
import com.familyapp.infrastructure.adventure.AdventureJpaRepository;
import com.familyapp.infrastructure.adventure.AdventureTicketGrantEntity;
import com.familyapp.infrastructure.adventure.AdventureTicketGrantJpaRepository;
import com.familyapp.infrastructure.adventure.ChildEggUnlockEntity;
import com.familyapp.infrastructure.adventure.ChildEggUnlockJpaRepository;
import com.familyapp.infrastructure.adventure.ChildInventoryEntity;
import com.familyapp.infrastructure.adventure.ChildInventoryJpaRepository;
import com.familyapp.infrastructure.adventure.LootItemEntity;
import com.familyapp.infrastructure.adventure.LootItemJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.ChildPetJpaRepository;
import com.familyapp.infrastructure.pet.PetHistoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adventures: a pet is sent out on a ticket, and returns with loot.
 *
 * The clock is server-authoritative -- an adventure is ready only when NOW is past
 * started_at + duration_secs -- and claiming is idempotent: the loot is resolved once,
 * stored on the adventure row, and re-reads return the stored result. Randomness is only
 * ever earned (a ticket per level cleared), never bought, which is what keeps this a
 * chore motivator rather than a loot box.
 */
@Service
@Transactional
public class AdventureService {

    private static final int DEFAULT_DURATION_SECS = 3600; // one hour
    private static final int FLOOR_FOOD_QTY = 1;           // never empty-handed

    // Weighted table, out of 100. The remainder after egg + frame falls to food, which is
    // also the floor when a roll cannot be satisfied (no unlockable egg, no active frame).
    private static final int EGG_WEIGHT = 20;
    private static final int FRAME_WEIGHT = 25;

    private static final String STATUS_ONGOING = "ONGOING";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final String FRAME_TYPE = "FRAME";
    private static final String SCENE_ITEM_TYPE = "SCENE_ITEM";
    private static final String FOOD_REF = "food";

    private final AdventureJpaRepository adventureRepository;
    private final AdventureTicketGrantJpaRepository ticketGrantRepository;
    private final ChildEggUnlockJpaRepository eggUnlockRepository;
    private final ChildInventoryJpaRepository inventoryRepository;
    private final LootItemJpaRepository lootItemRepository;
    private final CollectedFoodService collectedFoodService;
    private final ChildPetJpaRepository childPetRepository;
    private final PetHistoryJpaRepository petHistoryRepository;
    private final FamilyMemberJpaRepository memberRepository;

    public AdventureService(
            AdventureJpaRepository adventureRepository,
            AdventureTicketGrantJpaRepository ticketGrantRepository,
            ChildEggUnlockJpaRepository eggUnlockRepository,
            ChildInventoryJpaRepository inventoryRepository,
            LootItemJpaRepository lootItemRepository,
            CollectedFoodService collectedFoodService,
            ChildPetJpaRepository childPetRepository,
            PetHistoryJpaRepository petHistoryRepository,
            FamilyMemberJpaRepository memberRepository
    ) {
        this.adventureRepository = adventureRepository;
        this.ticketGrantRepository = ticketGrantRepository;
        this.eggUnlockRepository = eggUnlockRepository;
        this.inventoryRepository = inventoryRepository;
        this.lootItemRepository = lootItemRepository;
        this.collectedFoodService = collectedFoodService;
        this.childPetRepository = childPetRepository;
        this.petHistoryRepository = petHistoryRepository;
        this.memberRepository = memberRepository;
    }

    /** Give every child the four Commons if they do not have them yet. Idempotent, so it
     *  is safe to call on any interaction and covers members created after the migration. */
    public void ensureCommonsUnlocked(UUID memberId) {
        FamilyMemberEntity member = null;
        for (String egg : EggCatalog.COMMON_EGGS) {
            if (eggUnlockRepository.existsByMemberAndEggType(memberId, egg)) {
                continue;
            }
            if (member == null) {
                member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
            }
            var row = new ChildEggUnlockEntity();
            row.setId(UUID.randomUUID());
            row.setMember(member);
            row.setEggType(egg);
            row.setSource("default");
            row.setUnlockedAt(OffsetDateTime.now());
            eggUnlockRepository.save(row);
        }
    }

    /** Tickets earned minus tickets spent. Each started adventure spends one, so the
     *  balance is two row counts and can never drift out of a mutable counter. */
    @Transactional(readOnly = true)
    public long ticketBalance(UUID memberId) {
        return ticketGrantRepository.countByMemberId(memberId) - adventureRepository.countByMemberId(memberId);
    }

    /**
     * Grant one ticket for each level newly crossed. Idempotent via
     * UNIQUE(member_id, granted_for): reaching a level in a month grants exactly one
     * ticket ever, however many times the level-up path runs.
     */
    public void awardTicketsForLevelUp(UUID memberId, int year, int month, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel) {
            return;
        }
        var member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return;
        }
        String period = "%04d-%02d".formatted(year, month);
        for (int level = oldLevel + 1; level <= newLevel; level++) {
            String grantedFor = period + ":L" + level;
            if (ticketGrantRepository.existsByMemberAndGrantedFor(memberId, grantedFor)) {
                continue;
            }
            var grant = new AdventureTicketGrantEntity();
            grant.setId(UUID.randomUUID());
            grant.setMember(member);
            grant.setGrantedFor(grantedFor);
            grant.setGrantedAt(OffsetDateTime.now());
            ticketGrantRepository.save(grant);
        }
    }

    /** Spend a ticket and send the pet on an adventure. The saved row IS the spend. */
    public AdventureEntity start(UUID memberId, String scene) {
        ensureCommonsUnlocked(memberId);
        if (ticketBalance(memberId) <= 0) {
            throw new IllegalStateException("No adventure tickets available");
        }
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        var adventure = new AdventureEntity();
        adventure.setId(UUID.randomUUID());
        adventure.setMember(member);
        adventure.setScene(scene);
        adventure.setStartedAt(OffsetDateTime.now());
        adventure.setDurationSecs(DEFAULT_DURATION_SECS);
        adventure.setStatus(STATUS_ONGOING);
        adventure.setCreatedAt(OffsetDateTime.now());
        return adventureRepository.save(adventure);
    }

    /** Claim a finished adventure. Idempotent: a second claim returns the stored loot
     *  rather than resolving again. */
    public LootResult claim(UUID adventureId) {
        var adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new IllegalArgumentException("Adventure not found: " + adventureId));

        if (STATUS_CLAIMED.equals(adventure.getStatus())) {
            return storedLoot(adventure);
        }
        var readyAt = adventure.getStartedAt().plusSeconds(adventure.getDurationSecs());
        if (OffsetDateTime.now().isBefore(readyAt)) {
            throw new IllegalStateException("Adventure not finished yet");
        }

        UUID memberId = adventure.getMember().getId();
        LootResult loot = resolveLoot(memberId);
        applyLoot(memberId, loot);

        adventure.setLootType(loot.type().name());
        adventure.setLootRef(loot.ref());
        adventure.setLootQty(loot.quantity());
        adventure.setStatus(STATUS_CLAIMED);
        adventure.setClaimedAt(OffsetDateTime.now());
        adventureRepository.save(adventure);
        return loot;
    }

    private LootResult storedLoot(AdventureEntity adventure) {
        int qty = adventure.getLootQty() == null ? 0 : adventure.getLootQty();
        return new LootResult(LootType.valueOf(adventure.getLootType()), adventure.getLootRef(), qty);
    }

    /**
     * The rules, in order:
     *   1. The first adventure ever, or a child with no selectable egg left, is a
     *      guaranteed egg -- so the loop teaches itself and nobody is ever stranded with
     *      nothing to pick next month.
     *   2. Otherwise roll the weighted table, with food as the floor.
     * A guaranteed or rolled egg that cannot be satisfied (nothing left to unlock) falls
     * back to food, so there is always something to bring home.
     */
    private LootResult resolveLoot(UUID memberId) {
        boolean firstEver = adventureRepository.countByMemberIdAndStatus(memberId, STATUS_CLAIMED) == 0;
        boolean noSelectableEgg = selectableEggTypes(memberId).isEmpty();

        if (firstEver || noSelectableEgg) {
            return eggLootOrFood(memberId);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < EGG_WEIGHT) {
            return eggLootOrFood(memberId);
        }
        if (roll < EGG_WEIGHT + FRAME_WEIGHT) {
            return cosmeticLootOrFood();
        }
        return new LootResult(LootType.FOOD, FOOD_REF, FLOOR_FOOD_QTY);
    }

    /** Pick a not-yet-unlocked egg, escalating Rare -> Legendary -> Mythic; food if the
     *  child has already unlocked every egg there is. */
    private LootResult eggLootOrFood(UUID memberId) {
        var unlocked = new HashSet<>(eggUnlockRepository.findEggTypesByMemberId(memberId));
        for (EggRarity rarity : List.of(EggRarity.RARE, EggRarity.LEGENDARY, EggRarity.MYTHIC)) {
            var candidates = EggCatalog.eggTypesOfRarity(rarity).stream()
                    .filter(egg -> !unlocked.contains(egg))
                    .toList();
            if (!candidates.isEmpty()) {
                String egg = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                return new LootResult(LootType.EGG, egg, 1);
            }
        }
        return new LootResult(LootType.FOOD, FOOD_REF, FLOOR_FOOD_QTY);
    }

    /** A random active cosmetic (frame or scene item) from the catalog; food if none exist
     *  yet (before the art ships, the catalog is empty and cosmetics simply never drop). */
    private LootResult cosmeticLootOrFood() {
        var cosmetics = lootItemRepository.findByActiveTrue().stream()
                .filter(item -> FRAME_TYPE.equals(item.getType()) || SCENE_ITEM_TYPE.equals(item.getType()))
                .toList();
        if (cosmetics.isEmpty()) {
            return new LootResult(LootType.FOOD, FOOD_REF, FLOOR_FOOD_QTY);
        }
        var item = cosmetics.get(ThreadLocalRandom.current().nextInt(cosmetics.size()));
        var type = SCENE_ITEM_TYPE.equals(item.getType()) ? LootType.SCENE_ITEM : LootType.FRAME;
        return new LootResult(type, item.getId(), 1);
    }

    /** Unlocked eggs minus already collected (history) minus the current month's pet. */
    private Set<String> selectableEggTypes(UUID memberId) {
        var selectable = new HashSet<>(eggUnlockRepository.findEggTypesByMemberId(memberId));
        petHistoryRepository.findByMemberIdOrderByYearDescMonthDesc(memberId)
                .forEach(history -> selectable.remove(history.getSelectedEggType()));
        var now = LocalDate.now();
        childPetRepository.findByMemberIdAndYearAndMonth(memberId, now.getYear(), now.getMonthValue())
                .ifPresent(pet -> selectable.remove(pet.getSelectedEggType()));
        return selectable;
    }

    private void applyLoot(UUID memberId, LootResult loot) {
        switch (loot.type()) {
            case EGG -> unlockEgg(memberId, loot.ref());
            case FOOD -> collectedFoodService.addBonusFood(memberId, loot.quantity());
            case FRAME -> grantInventoryItem(memberId, loot.ref());
            case SCENE_ITEM -> grantInventoryItem(memberId, loot.ref());
        }
    }

    private void unlockEgg(UUID memberId, String eggType) {
        if (eggUnlockRepository.existsByMemberAndEggType(memberId, eggType)) {
            return;
        }
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        var row = new ChildEggUnlockEntity();
        row.setId(UUID.randomUUID());
        row.setMember(member);
        row.setEggType(eggType);
        row.setSource("adventure");
        row.setUnlockedAt(OffsetDateTime.now());
        eggUnlockRepository.save(row);
    }

    private void grantInventoryItem(UUID memberId, String itemId) {
        if (inventoryRepository.existsByMemberAndItem(memberId, itemId)) {
            return;
        }
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        var row = new ChildInventoryEntity();
        row.setId(UUID.randomUUID());
        row.setMember(member);
        row.setItemId(itemId);
        row.setAcquiredAt(OffsetDateTime.now());
        inventoryRepository.save(row);
    }
}
