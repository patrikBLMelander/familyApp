package com.familyapp.application.adventure;

import com.familyapp.application.pet.CollectedFoodService;
import com.familyapp.domain.adventure.LootResult;
import com.familyapp.domain.adventure.LootType;
import com.familyapp.infrastructure.adventure.AdventureEntity;
import com.familyapp.infrastructure.adventure.AdventureJpaRepository;
import com.familyapp.infrastructure.adventure.AdventureTicketGrantEntity;
import com.familyapp.infrastructure.adventure.AdventureTicketGrantJpaRepository;
import com.familyapp.infrastructure.adventure.ChildEggUnlockEntity;
import com.familyapp.infrastructure.adventure.ChildEggUnlockJpaRepository;
import com.familyapp.infrastructure.adventure.ChildInventoryJpaRepository;
import com.familyapp.infrastructure.adventure.LootItemJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.ChildPetJpaRepository;
import com.familyapp.infrastructure.pet.PetHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The adventure loop is earned reward, so the rules that matter are the guarantees, the
 * server-authoritative clock, and idempotency -- not the random rolls. Those are what is
 * pinned here.
 */
class AdventureServiceTest {

    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID ADVENTURE = UUID.randomUUID();
    private static final List<String> ALL_EGGS = List.of(
            "green_egg", "red_egg", "purple_egg", "yellow_egg",
            "orange_egg", "black_egg", "cyan_egg", "gray_egg", "brown_egg", "silver_egg",
            "sand_egg", "ice_egg", "amber_egg", "clay_egg", "ember_egg", "indigo_egg", "golden_egg", "white_egg", "frost_egg", "tiger_egg", "snow_egg", "savanna_egg", "ivory_egg", "swamp_egg", "onyx_egg", "moon_egg", "blue_egg", "teal_egg", "pink_egg");
    private static final List<String> ONLY_COMMONS = List.of("green_egg", "red_egg", "purple_egg", "yellow_egg");

    private AdventureJpaRepository adventures;
    private AdventureTicketGrantJpaRepository grants;
    private ChildEggUnlockJpaRepository unlocks;
    private ChildInventoryJpaRepository inventory;
    private LootItemJpaRepository lootItems;
    private CollectedFoodService food;
    private ChildPetJpaRepository childPets;
    private PetHistoryJpaRepository petHistory;
    private FamilyMemberJpaRepository members;
    private FamilyMemberEntity member;
    private AdventureService service;

    @BeforeEach
    void setUp() {
        adventures = mock(AdventureJpaRepository.class);
        grants = mock(AdventureTicketGrantJpaRepository.class);
        unlocks = mock(ChildEggUnlockJpaRepository.class);
        inventory = mock(ChildInventoryJpaRepository.class);
        lootItems = mock(LootItemJpaRepository.class);
        food = mock(CollectedFoodService.class);
        childPets = mock(ChildPetJpaRepository.class);
        petHistory = mock(PetHistoryJpaRepository.class);
        members = mock(FamilyMemberJpaRepository.class);
        member = mock(FamilyMemberEntity.class);
        lenient().when(member.getId()).thenReturn(MEMBER);
        lenient().when(members.findById(MEMBER)).thenReturn(Optional.of(member));

        // Default: no frames in the catalog yet, nothing collected, no current pet.
        lenient().when(lootItems.findByTypeAndActiveTrue("FRAME")).thenReturn(List.of());
        lenient().when(petHistory.findByMemberIdOrderByYearDescMonthDesc(MEMBER)).thenReturn(List.of());
        lenient().when(childPets.findByMemberIdAndYearAndMonth(eq(MEMBER), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        service = new AdventureService(adventures, grants, unlocks, inventory, lootItems,
                food, childPets, petHistory, members);
    }

    private AdventureEntity ongoing(OffsetDateTime startedAt, int durationSecs) {
        var a = new AdventureEntity();
        a.setId(ADVENTURE);
        a.setMember(member);
        a.setScene("forest");
        a.setStartedAt(startedAt);
        a.setDurationSecs(durationSecs);
        a.setStatus("ONGOING");
        return a;
    }

    @Test
    void firstAdventureGuaranteesAnEgg() {
        when(adventures.findById(ADVENTURE)).thenReturn(Optional.of(ongoing(OffsetDateTime.now().minusHours(2), 3600)));
        when(adventures.countByMemberIdAndStatus(MEMBER, "CLAIMED")).thenReturn(0L);
        when(unlocks.findEggTypesByMemberId(MEMBER)).thenReturn(ONLY_COMMONS);
        when(unlocks.existsByMemberAndEggType(eq(MEMBER), any())).thenReturn(false);

        LootResult loot = service.claim(ADVENTURE);

        assertThat(loot.type()).isEqualTo(LootType.EGG);
        assertThat(EggCatalog.rarityOf(loot.ref()).name()).isIn("RARE", "LEGENDARY", "MYTHIC");
        verify(unlocks).save(any(ChildEggUnlockEntity.class));
        verify(food, never()).addBonusFood(any(), anyInt());
    }

    @Test
    void claimBeforeFinishIsRejected() {
        when(adventures.findById(ADVENTURE)).thenReturn(Optional.of(ongoing(OffsetDateTime.now(), 3600)));

        assertThatThrownBy(() -> service.claim(ADVENTURE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not finished");
        verify(adventures, never()).save(any());
    }

    @Test
    void claimingAgainReturnsStoredLootWithoutResolving() {
        var claimed = ongoing(OffsetDateTime.now().minusHours(2), 3600);
        claimed.setStatus("CLAIMED");
        claimed.setLootType("FOOD");
        claimed.setLootRef("food");
        claimed.setLootQty(1);
        when(adventures.findById(ADVENTURE)).thenReturn(Optional.of(claimed));

        LootResult loot = service.claim(ADVENTURE);

        assertThat(loot).isEqualTo(new LootResult(LootType.FOOD, "food", 1));
        verify(adventures, never()).save(any());
        verify(food, never()).addBonusFood(any(), anyInt());
    }

    @Test
    void guaranteedEggFallsBackToFoodWhenEverythingIsUnlocked() {
        when(adventures.findById(ADVENTURE)).thenReturn(Optional.of(ongoing(OffsetDateTime.now().minusHours(2), 3600)));
        when(adventures.countByMemberIdAndStatus(MEMBER, "CLAIMED")).thenReturn(0L); // first ever -> guaranteed egg
        when(unlocks.findEggTypesByMemberId(MEMBER)).thenReturn(ALL_EGGS); // nothing left to unlock

        LootResult loot = service.claim(ADVENTURE);

        assertThat(loot).isEqualTo(new LootResult(LootType.FOOD, "food", 1));
        verify(food).addBonusFood(MEMBER, 1);
        verify(unlocks, never()).save(any());
    }

    @Test
    void ticketsAreGrantedOncePerCrossedLevel() {
        when(grants.existsByMemberAndGrantedFor(eq(MEMBER), any())).thenReturn(false);

        service.awardTicketsForLevelUp(MEMBER, 2026, 9, 1, 3);

        var captor = ArgumentCaptor.forClass(AdventureTicketGrantEntity.class);
        verify(grants, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AdventureTicketGrantEntity::getGrantedFor)
                .containsExactly("2026-09:L2", "2026-09:L3");
    }

    @Test
    void alreadyGrantedTicketIsNotDuplicated() {
        when(grants.existsByMemberAndGrantedFor(MEMBER, "2026-09:L2")).thenReturn(true);
        when(grants.existsByMemberAndGrantedFor(MEMBER, "2026-09:L3")).thenReturn(false);

        service.awardTicketsForLevelUp(MEMBER, 2026, 9, 1, 3);

        var captor = ArgumentCaptor.forClass(AdventureTicketGrantEntity.class);
        verify(grants, times(1)).save(captor.capture());
        assertThat(captor.getValue().getGrantedFor()).isEqualTo("2026-09:L3");
    }

    @Test
    void noTicketWhenLevelDidNotRise() {
        service.awardTicketsForLevelUp(MEMBER, 2026, 9, 3, 3);
        verify(grants, never()).save(any());
    }

    @Test
    void starMilestonesGrantOneTicketPerFiftyXpPastMax() {
        when(grants.existsByMemberAndGrantedFor(eq(MEMBER), any())).thenReturn(false);

        // 120 -> 230 XP crosses max (125) and milestones at 175 and 225 -> two tickets.
        service.awardTicketsForStarMilestones(MEMBER, 2026, 9, 120, 230);

        var captor = ArgumentCaptor.forClass(AdventureTicketGrantEntity.class);
        verify(grants, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AdventureTicketGrantEntity::getGrantedFor)
                .containsExactly("2026-09:STAR1", "2026-09:STAR2");
    }

    @Test
    void starTicketsContinuePastTheFifthStar() {
        when(grants.existsByMemberAndGrantedFor(eq(MEMBER), any())).thenReturn(false);

        // 5 stars is at 375 XP (milestone 5); 375 -> 425 is milestone 6 -> still a ticket.
        service.awardTicketsForStarMilestones(MEMBER, 2026, 9, 375, 425);

        var captor = ArgumentCaptor.forClass(AdventureTicketGrantEntity.class);
        verify(grants, times(1)).save(captor.capture());
        assertThat(captor.getValue().getGrantedFor()).isEqualTo("2026-09:STAR6");
    }

    @Test
    void noStarTicketBelowMaxLevel() {
        service.awardTicketsForStarMilestones(MEMBER, 2026, 9, 10, 124);
        verify(grants, never()).save(any());
    }

    @Test
    void ticketBalanceIsGrantsMinusAdventures() {
        when(grants.countByMemberId(MEMBER)).thenReturn(4L);
        when(adventures.countByMemberId(MEMBER)).thenReturn(1L);

        assertThat(service.ticketBalance(MEMBER)).isEqualTo(3L);
    }

    @Test
    void startWithoutATicketIsRejected() {
        when(unlocks.existsByMemberAndEggType(eq(MEMBER), any())).thenReturn(true); // commons already unlocked
        when(grants.countByMemberId(MEMBER)).thenReturn(0L);
        when(adventures.countByMemberId(MEMBER)).thenReturn(0L);

        assertThatThrownBy(() -> service.start(MEMBER, "forest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No adventure tickets");
        verify(adventures, never()).save(any());
    }
}
