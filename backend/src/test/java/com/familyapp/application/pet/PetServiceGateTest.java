package com.familyapp.application.pet;

import com.familyapp.application.adventure.AdventureService;
import com.familyapp.application.xp.XpService;
import com.familyapp.infrastructure.adventure.ChildEggUnlockJpaRepository;
import com.familyapp.infrastructure.adventure.ChildInventoryJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.ChildPetEntity;
import com.familyapp.infrastructure.pet.ChildPetJpaRepository;
import com.familyapp.infrastructure.pet.PetHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.familyapp.domain.pet.ChildPet;
import com.familyapp.infrastructure.pet.PetHistoryEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The egg-picker gate: only unlocked eggs can be picked, and the picker reports which of
 * the fourteen are unlocked / collected. Commons must always read as available.
 */
class PetServiceGateTest {

    private static final UUID MEMBER = UUID.randomUUID();

    private ChildPetJpaRepository pets;
    private PetHistoryJpaRepository history;
    private FamilyMemberJpaRepository members;
    private XpService xp;
    private ChildEggUnlockJpaRepository unlocks;
    private ChildInventoryJpaRepository inventory;
    private AdventureService adventure;
    private FamilyMemberEntity member;
    private PetService service;

    @BeforeEach
    void setUp() {
        pets = mock(ChildPetJpaRepository.class);
        history = mock(PetHistoryJpaRepository.class);
        members = mock(FamilyMemberJpaRepository.class);
        xp = mock(XpService.class);
        unlocks = mock(ChildEggUnlockJpaRepository.class);
        inventory = mock(ChildInventoryJpaRepository.class);
        adventure = mock(AdventureService.class);
        member = mock(FamilyMemberEntity.class);
        lenient().when(member.getId()).thenReturn(MEMBER);
        lenient().when(member.getRole()).thenReturn("CHILD");
        lenient().when(members.findById(MEMBER)).thenReturn(Optional.of(member));
        lenient().when(history.findByMemberIdOrderByYearDescMonthDesc(MEMBER)).thenReturn(List.of());

        service = new PetService(pets, history, members, xp, unlocks, inventory, adventure);
    }

    @Test
    void pickingALockedEggIsRejected() {
        when(unlocks.existsByMemberAndEggType(MEMBER, "blue_egg")).thenReturn(false); // legendary, not unlocked

        assertThatThrownBy(() -> service.selectEgg(MEMBER, "blue_egg", "Bamse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not unlocked");
        verify(pets, never()).save(any());
    }

    @Test
    void pickingAnUnlockedCommonSucceeds() {
        when(unlocks.existsByMemberAndEggType(MEMBER, "green_egg")).thenReturn(true);
        when(pets.findByMemberIdAndYearAndMonth(eq(MEMBER), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        when(pets.save(any(ChildPetEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var pet = service.selectEgg(MEMBER, "green_egg", "Måns");

        assertThat(pet.selectedEggType()).isEqualTo("green_egg");
        assertThat(pet.petType()).isEqualTo("cat");
        verify(pets).save(any(ChildPetEntity.class));
    }

    @Test
    void eggOptionsMarkCommonsUnlockedAndRaritiesLocked() {
        when(unlocks.findEggTypesByMemberId(MEMBER))
                .thenReturn(List.of("green_egg", "red_egg", "purple_egg", "yellow_egg"));

        var options = service.getEggOptions(MEMBER);

        assertThat(options).hasSize(29);
        var green = options.stream().filter(o -> o.eggType().equals("green_egg")).findFirst().orElseThrow();
        assertThat(green.rarity()).isEqualTo("COMMON");
        assertThat(green.unlocked()).isTrue();
        var dragon = options.stream().filter(o -> o.eggType().equals("blue_egg")).findFirst().orElseThrow();
        assertThat(dragon.rarity()).isEqualTo("MYTHIC");
        assertThat(dragon.unlocked()).isFalse();
        var koala = options.stream().filter(o -> o.eggType().equals("silver_egg")).findFirst().orElseThrow();
        assertThat(koala.rarity()).isEqualTo("RARE");
        assertThat(koala.unlocked()).isFalse();
    }

    // ---- first-pet grace: a late first pet follows into next month

    @Test
    void graceWindowIsTheLastTenDaysOfTheMonth() {
        assertThat(ChildPet.inFirstPetGraceWindow(LocalDate.of(2026, 9, 20))).isFalse();
        assertThat(ChildPet.inFirstPetGraceWindow(LocalDate.of(2026, 9, 21))).isTrue();
        assertThat(ChildPet.inFirstPetGraceWindow(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(ChildPet.inFirstPetGraceWindow(LocalDate.of(2026, 2, 18))).isFalse();
        assertThat(ChildPet.inFirstPetGraceWindow(LocalDate.of(2026, 2, 19))).isTrue();
    }

    @Test
    void lateFirstPetFollowsIntoNextMonth() {
        assertThat(service.followsIntoNextMonth(MEMBER, 2026, 9, hatched(2026, 9, 28))).isTrue();
    }

    @Test
    void earlyFirstPetDoesNotFollow() {
        assertThat(service.followsIntoNextMonth(MEMBER, 2026, 9, hatched(2026, 9, 10))).isFalse();
    }

    @Test
    void lateSecondPetDoesNotFollow() {
        when(history.findByMemberIdOrderByYearDescMonthDesc(MEMBER)).thenReturn(List.of(new PetHistoryEntity()));

        assertThat(service.followsIntoNextMonth(MEMBER, 2026, 9, hatched(2026, 9, 28))).isFalse();
    }

    @Test
    void alreadyCarriedPetDoesNotFollowAgain() {
        // Hatched late in September, now sitting in October: the grace is spent.
        assertThat(service.followsIntoNextMonth(MEMBER, 2026, 10, hatched(2026, 9, 28))).isFalse();
    }

    private static OffsetDateTime hatched(int year, int month, int day) {
        return LocalDate.of(year, month, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
