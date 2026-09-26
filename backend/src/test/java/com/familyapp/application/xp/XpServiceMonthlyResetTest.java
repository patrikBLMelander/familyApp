package com.familyapp.application.xp;

import com.familyapp.application.adventure.AdventureService;
import com.familyapp.application.pet.PetService;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.pet.ChildPetEntity;
import com.familyapp.infrastructure.pet.ChildPetJpaRepository;
import com.familyapp.infrastructure.pet.PetHistoryEntity;
import com.familyapp.infrastructure.pet.PetHistoryJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpHistoryEntity;
import com.familyapp.infrastructure.xp.MemberXpHistoryJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpProgressEntity;
import com.familyapp.infrastructure.xp.MemberXpProgressJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The monthly reset: every pet goes to history and XP starts over -- except a late first
 * pet, which follows its child into the new month with its XP.
 */
class XpServiceMonthlyResetTest {

    private static final LocalDate LAST_MONTH = LocalDate.now().minusMonths(1);
    private static final LocalDate THIS_MONTH = LocalDate.now();

    private MemberXpProgressJpaRepository progress;
    private MemberXpHistoryJpaRepository xpHistory;
    private ChildPetJpaRepository pets;
    private PetHistoryJpaRepository petHistory;
    private PetService petService;
    private XpService service;

    @BeforeEach
    void setUp() {
        progress = mock(MemberXpProgressJpaRepository.class);
        xpHistory = mock(MemberXpHistoryJpaRepository.class);
        pets = mock(ChildPetJpaRepository.class);
        petHistory = mock(PetHistoryJpaRepository.class);
        petService = mock(PetService.class);
        service = new XpService(progress, xpHistory, mock(FamilyMemberJpaRepository.class),
                pets, petHistory, petService, mock(AdventureService.class));
    }

    @Test
    void lateFirstPetKeepsItsXpAndMovesIntoTheNewMonth() {
        var child = member();
        var xp = progressFor(child, 40, 3);
        var pet = petFor(child);
        when(progress.findAll()).thenReturn(List.of(xp));
        when(pets.findAll()).thenReturn(List.of(pet));
        when(petService.followsIntoNextMonth(eq(child.getId()), anyInt(), anyInt(), any())).thenReturn(true);

        service.monthlyReset();

        assertThat(xp.getCurrentXp()).isEqualTo(40);
        assertThat(xp.getCurrentLevel()).isEqualTo(3);
        assertThat(xp.getMonth()).isEqualTo(THIS_MONTH.getMonthValue());
        // XP history is still written: the level allowance reads last month's level from it.
        verify(xpHistory).save(any(MemberXpHistoryEntity.class));
        assertThat(pet.getMonth()).isEqualTo(THIS_MONTH.getMonthValue());
        assertThat(pet.getYear()).isEqualTo(THIS_MONTH.getYear());
        verify(petHistory, never()).save(any(PetHistoryEntity.class));
        verify(pets, never()).delete(any());
    }

    @Test
    void otherPetsGoToHistoryAndXpStartsOver() {
        var child = member();
        var xp = progressFor(child, 40, 3);
        var pet = petFor(child);
        when(progress.findAll()).thenReturn(List.of(xp));
        when(pets.findAll()).thenReturn(List.of(pet));
        when(petService.followsIntoNextMonth(eq(child.getId()), anyInt(), anyInt(), any())).thenReturn(false);

        service.monthlyReset();

        assertThat(xp.getCurrentXp()).isZero();
        assertThat(xp.getCurrentLevel()).isEqualTo(1);
        verify(petHistory).save(any(PetHistoryEntity.class));
        verify(pets).delete(pet);
    }

    private static FamilyMemberEntity member() {
        var member = mock(FamilyMemberEntity.class);
        when(member.getId()).thenReturn(UUID.randomUUID());
        return member;
    }

    private static MemberXpProgressEntity progressFor(FamilyMemberEntity member, int xp, int level) {
        var entity = new MemberXpProgressEntity();
        entity.setId(UUID.randomUUID());
        entity.setMember(member);
        entity.setYear(LAST_MONTH.getYear());
        entity.setMonth(LAST_MONTH.getMonthValue());
        entity.setCurrentXp(xp);
        entity.setCurrentLevel(level);
        entity.setTotalTasksCompleted(4);
        return entity;
    }

    private static ChildPetEntity petFor(FamilyMemberEntity member) {
        var entity = new ChildPetEntity();
        entity.setId(UUID.randomUUID());
        entity.setMember(member);
        entity.setYear(LAST_MONTH.getYear());
        entity.setMonth(LAST_MONTH.getMonthValue());
        entity.setSelectedEggType("green_egg");
        entity.setPetType("cat");
        entity.setGrowthStage(3);
        entity.setHatchedAt(OffsetDateTime.now().minusDays(5));
        return entity;
    }
}
