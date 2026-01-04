package com.familyapp.application.xp;

import com.familyapp.domain.xp.MemberXpHistory;
import com.familyapp.domain.xp.MemberXpProgress;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpHistoryEntity;
import com.familyapp.infrastructure.xp.MemberXpHistoryJpaRepository;
import com.familyapp.infrastructure.xp.MemberXpProgressEntity;
import com.familyapp.infrastructure.xp.MemberXpProgressJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class XpService {

    private final MemberXpProgressJpaRepository progressRepository;
    private final MemberXpHistoryJpaRepository historyRepository;
    private final FamilyMemberJpaRepository memberRepository;

    public XpService(
            MemberXpProgressJpaRepository progressRepository,
            MemberXpHistoryJpaRepository historyRepository,
            FamilyMemberJpaRepository memberRepository
    ) {
        this.progressRepository = progressRepository;
        this.historyRepository = historyRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Award XP to a member when they complete a task
     * Only awards XP if the member is a child (CHILD role)
     */
    public void awardXp(UUID memberId, int xpPoints) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Only award XP to children
        if (!"CHILD".equals(member.getRole())) {
            return;
        }

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // Get or create progress for current month
        var progressEntity = progressRepository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .orElseGet(() -> {
                    var newProgress = new MemberXpProgressEntity();
                    newProgress.setId(UUID.randomUUID());
                    newProgress.setMember(member);
                    newProgress.setYear(year);
                    newProgress.setMonth(month);
                    newProgress.setCurrentXp(0);
                    newProgress.setCurrentLevel(1);
                    newProgress.setTotalTasksCompleted(0);
                    newProgress.setCreatedAt(OffsetDateTime.now());
                    newProgress.setUpdatedAt(OffsetDateTime.now());
                    return newProgress;
                });

        // Update XP and level
        int newXp = progressEntity.getCurrentXp() + xpPoints;
        int newLevel = MemberXpProgress.calculateLevel(newXp);
        
        progressEntity.setCurrentXp(newXp);
        progressEntity.setCurrentLevel(newLevel);
        progressEntity.setTotalTasksCompleted(progressEntity.getTotalTasksCompleted() + 1);
        progressEntity.setUpdatedAt(OffsetDateTime.now());

        progressRepository.save(progressEntity);
    }

    /**
     * Remove XP from a member when they uncheck a task
     */
    public void removeXp(UUID memberId, int xpPoints) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

        // Only remove XP from children
        if (!"CHILD".equals(member.getRole())) {
            return;
        }

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        var progressEntity = progressRepository.findByMemberIdAndYearAndMonth(memberId, year, month);
        if (progressEntity.isEmpty()) {
            return; // No progress to update
        }

        var progress = progressEntity.get();
        int newXp = Math.max(0, progress.getCurrentXp() - xpPoints);
        int newLevel = MemberXpProgress.calculateLevel(newXp);
        
        progress.setCurrentXp(newXp);
        progress.setCurrentLevel(newLevel);
        progress.setTotalTasksCompleted(Math.max(0, progress.getTotalTasksCompleted() - 1));
        progress.setUpdatedAt(OffsetDateTime.now());

        progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public Optional<MemberXpProgress> getCurrentProgress(UUID memberId) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        return progressRepository.findByMemberIdAndYearAndMonth(memberId, year, month)
                .map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public List<MemberXpHistory> getHistory(UUID memberId) {
        return historyRepository.findByMemberIdOrderByYearDescMonthDesc(memberId).stream()
                .map(this::toHistoryDomain)
                .toList();
    }

    /**
     * Monthly reset: Move current month's progress to history and reset progress
     * Runs on the first day of each month at 00:00
     */
    @Scheduled(cron = "0 0 0 1 * ?") // First day of month at midnight
    public void monthlyReset() {
        LocalDate now = LocalDate.now();
        int previousYear = now.minusMonths(1).getYear();
        int previousMonth = now.minusMonths(1).getMonthValue();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        // Get all progress entries from previous month
        var previousMonthProgress = progressRepository.findAll().stream()
                .filter(p -> p.getYear() == previousYear && p.getMonth() == previousMonth)
                .toList();

        for (var progress : previousMonthProgress) {
            // Save to history
            var history = new MemberXpHistoryEntity();
            history.setId(UUID.randomUUID());
            history.setMember(progress.getMember());
            history.setYear(previousYear);
            history.setMonth(previousMonth);
            history.setFinalXp(progress.getCurrentXp());
            history.setFinalLevel(progress.getCurrentLevel());
            history.setTotalTasksCompleted(progress.getTotalTasksCompleted());
            history.setCreatedAt(OffsetDateTime.now());
            historyRepository.save(history);

            // Reset progress for current month
            progress.setYear(currentYear);
            progress.setMonth(currentMonth);
            progress.setCurrentXp(0);
            progress.setCurrentLevel(1);
            progress.setTotalTasksCompleted(0);
            progress.setUpdatedAt(OffsetDateTime.now());
            progressRepository.save(progress);
        }
    }

    private MemberXpProgress toDomain(MemberXpProgressEntity entity) {
        return new MemberXpProgress(
                entity.getId(),
                entity.getMember().getId(),
                entity.getYear(),
                entity.getMonth(),
                entity.getCurrentXp(),
                entity.getCurrentLevel(),
                entity.getTotalTasksCompleted(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MemberXpHistory toHistoryDomain(MemberXpHistoryEntity entity) {
        return new MemberXpHistory(
                entity.getId(),
                entity.getMember().getId(),
                entity.getYear(),
                entity.getMonth(),
                entity.getFinalXp(),
                entity.getFinalLevel(),
                entity.getTotalTasksCompleted(),
                entity.getCreatedAt()
        );
    }
}

