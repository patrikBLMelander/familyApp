package com.familyapp.application.chore;

import com.familyapp.application.pet.CollectedFoodService;
import com.familyapp.domain.chore.DailyChore;
import com.familyapp.domain.chore.DailyChoreCompletion;
import com.familyapp.infrastructure.chore.DailyChoreCompletionEntity;
import com.familyapp.infrastructure.chore.DailyChoreCompletionJpaRepository;
import com.familyapp.infrastructure.chore.DailyChoreEntity;
import com.familyapp.infrastructure.chore.DailyChoreJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyChoreService {

    private final DailyChoreJpaRepository choreRepository;
    private final DailyChoreCompletionJpaRepository completionRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final CollectedFoodService foodService;

    public DailyChoreService(
            DailyChoreJpaRepository choreRepository,
            DailyChoreCompletionJpaRepository completionRepository,
            FamilyMemberJpaRepository memberRepository,
            CollectedFoodService foodService
    ) {
        this.choreRepository = choreRepository;
        this.completionRepository = completionRepository;
        this.memberRepository = memberRepository;
        this.foodService = foodService;
    }

    public record DailyChoreWithCompletion(DailyChore chore, boolean completed, String completionId) {}

    @Transactional(readOnly = true)
    public List<DailyChore> getChoresForMember(UUID requesterId, UUID memberId) {
        validateSameFamily(requesterId, memberId);
        return choreRepository.findByMemberIdAndIsActiveTrue(memberId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyChoreWithCompletion> getChoresForDate(UUID requesterId, UUID memberId, LocalDate date) {
        validateSameFamily(requesterId, memberId);

        // 3-char abbreviation of weekday: MON, TUE, WED, THU, FRI, SAT, SUN
        String dayAbbrev = date.getDayOfWeek().name().substring(0, 3);

        var allChores = choreRepository.findByMemberIdAndIsActiveTrue(memberId);
        var dayChores = allChores.stream()
                .filter(c -> Arrays.asList(c.getWeekdays().split(",")).contains(dayAbbrev))
                .toList();

        if (dayChores.isEmpty()) {
            return List.of();
        }

        var choreIds = dayChores.stream().map(DailyChoreEntity::getId).toList();
        var completions = completionRepository.findByChoreIdsAndMemberIdAndDate(choreIds, memberId, date);
        Set<UUID> completedChoreIds = completions.stream()
                .map(c -> c.getChore().getId())
                .collect(Collectors.toSet());

        return dayChores.stream()
                .map(c -> {
                    String completionId = completions.stream()
                            .filter(comp -> comp.getChore().getId().equals(c.getId()))
                            .findFirst()
                            .map(comp -> comp.getId().toString())
                            .orElse(null);
                    return new DailyChoreWithCompletion(toDomain(c), completedChoreIds.contains(c.getId()), completionId);
                })
                .toList();
    }

    public DailyChore createChore(UUID requesterId, UUID memberId, String title, List<String> weekdays, int xpPoints) {
        validateSameFamily(requesterId, memberId);

        var memberEntity = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        var entity = new DailyChoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setFamily(memberEntity.getFamily());
        entity.setMember(memberEntity);
        entity.setTitle(title);
        entity.setWeekdays(String.join(",", weekdays));
        entity.setXpPoints(xpPoints);
        entity.setActive(true);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        return toDomain(choreRepository.save(entity));
    }

    public void deleteChore(UUID requesterId, UUID choreId) {
        var chore = choreRepository.findById(choreId)
                .orElseThrow(() -> new IllegalArgumentException("Chore not found"));

        validateSameFamilyByIds(requesterId, chore.getFamily().getId());
        choreRepository.delete(chore);
    }

    public DailyChoreCompletion markCompleted(UUID requesterId, UUID choreId, LocalDate date) {
        var chore = choreRepository.findById(choreId)
                .orElseThrow(() -> new IllegalArgumentException("Chore not found"));

        validateSameFamilyByIds(requesterId, chore.getFamily().getId());

        var existing = completionRepository.findByChore_IdAndMember_IdAndOccurrenceDate(
                choreId, chore.getMember().getId(), date);
        if (existing.isPresent()) {
            return toCompletionDomain(existing.get());
        }

        var entity = new DailyChoreCompletionEntity();
        entity.setId(UUID.randomUUID());
        entity.setChore(chore);
        entity.setMember(chore.getMember());
        entity.setOccurrenceDate(date);
        entity.setCompletedAt(OffsetDateTime.now());

        var saved = completionRepository.save(entity);

        // Add food when chore is completed
        if (chore.getXpPoints() > 0) {
            try {
                foodService.addBonusFood(chore.getMember().getId(), chore.getXpPoints());
            } catch (Exception e) {
                System.err.println("Failed to add food for chore completion: choreId=" + choreId + ", error=" + e.getMessage());
            }
        }

        return toCompletionDomain(saved);
    }

    public void unmarkCompleted(UUID requesterId, UUID choreId, LocalDate date) {
        var chore = choreRepository.findById(choreId)
                .orElseThrow(() -> new IllegalArgumentException("Chore not found"));

        validateSameFamilyByIds(requesterId, chore.getFamily().getId());

        var completion = completionRepository.findByChore_IdAndMember_IdAndOccurrenceDate(choreId, chore.getMember().getId(), date);
        if (completion.isPresent()) {
            if (chore.getXpPoints() > 0) {
                int unfedCount = foodService.getUnfedFoodCount(chore.getMember().getId());
                if (unfedCount < chore.getXpPoints()) {
                    throw new IllegalArgumentException(
                            String.format("Kan inte avmarkera syssla: Du har inte tillräckligt med omatad mat. " +
                                    "Du behöver %d omatad mat, men har bara %d omatad mat totalt.",
                                    chore.getXpPoints(), unfedCount)
                    );
                }
                foodService.removeFood(chore.getMember().getId(), chore.getXpPoints());
            }
            completionRepository.delete(completion.get());
        }
    }

    private void validateSameFamily(UUID requesterId, UUID memberId) {
        if (requesterId.equals(memberId)) return;
        var requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (!requester.getFamily().getId().equals(member.getFamily().getId())) {
            throw new IllegalArgumentException("Access denied: not in same family");
        }
    }

    private void validateSameFamilyByIds(UUID requesterId, UUID familyId) {
        var requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        if (!requester.getFamily().getId().equals(familyId)) {
            throw new IllegalArgumentException("Access denied: not in same family");
        }
    }

    private DailyChore toDomain(DailyChoreEntity e) {
        return new DailyChore(
                e.getId(),
                e.getFamily().getId(),
                e.getMember().getId(),
                e.getTitle(),
                Arrays.asList(e.getWeekdays().split(",")),
                e.getXpPoints(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private DailyChoreCompletion toCompletionDomain(DailyChoreCompletionEntity e) {
        return new DailyChoreCompletion(
                e.getId(),
                e.getChore().getId(),
                e.getMember().getId(),
                e.getOccurrenceDate(),
                e.getCompletedAt()
        );
    }
}
