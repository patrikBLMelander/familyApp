package com.familyapp.application.menstrualcycle;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.domain.menstrualcycle.MenstrualCycle;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.menstrualcycle.MenstrualCycleEntity;
import com.familyapp.infrastructure.menstrualcycle.MenstrualCycleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MenstrualCycleService {

    private final MenstrualCycleJpaRepository repository;
    private final FamilyMemberJpaRepository memberRepository;
    private final FamilyMemberService familyMemberService;

    public MenstrualCycleService(
            MenstrualCycleJpaRepository repository,
            FamilyMemberJpaRepository memberRepository,
            FamilyMemberService familyMemberService
    ) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.familyMemberService = familyMemberService;
    }

    /**
     * Get all menstrual cycle entries for a member.
     * Validates that the requester has permission to view the data.
     */
    @Transactional(readOnly = true)
    public List<MenstrualCycle> getEntries(UUID memberId, UUID requesterId) {
        validateAccess(memberId, requesterId);
        
        return repository.findByFamilyMemberIdOrderByPeriodStartDateDesc(memberId).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * Create a new menstrual cycle entry.
     * Automatically calculates cycle length based on previous entry.
     */
    public MenstrualCycle createEntry(UUID memberId, LocalDate periodStartDate, Integer periodLength, UUID requesterId) {
        validateAccess(memberId, requesterId);
        
        // Validate that member has menstrual cycle tracking enabled
        var member = familyMemberService.getMemberById(memberId);
        if (member.menstrualCycleEnabled() == null || !member.menstrualCycleEnabled()) {
            throw new IllegalArgumentException("Menstrual cycle tracking is not enabled for this member");
        }
        
        // Validate period start date is not in the future
        if (periodStartDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Period start date cannot be in the future");
        }
        
        // Validate period start date is not too far in the past (more than 10 years)
        if (periodStartDate.isBefore(LocalDate.now().minusYears(10))) {
            throw new IllegalArgumentException("Period start date cannot be more than 10 years in the past");
        }
        
        // Validate period length (1-15 days is reasonable)
        int actualPeriodLength = periodLength != null ? periodLength : 5;
        if (actualPeriodLength < 1 || actualPeriodLength > 15) {
            throw new IllegalArgumentException("Period length must be between 1 and 15 days");
        }
        
        // Check for overlapping entries
        var periodEndDate = periodStartDate.plusDays(actualPeriodLength - 1);
        var existingEntries = repository.findByFamilyMemberIdOrderByPeriodStartDateDesc(memberId);
        for (var existingEntry : existingEntries) {
            var existingStart = existingEntry.getPeriodStartDate();
            var existingLength = existingEntry.getPeriodLength() != null ? existingEntry.getPeriodLength() : 5;
            var existingEnd = existingStart.plusDays(existingLength - 1);
            
            // Check if periods overlap
            if (!periodEndDate.isBefore(existingStart) && !periodStartDate.isAfter(existingEnd)) {
                throw new IllegalArgumentException(
                    String.format("Period overlaps with existing entry from %s to %s", 
                        existingStart, existingEnd));
            }
        }
        
        var entity = new MenstrualCycleEntity();
        entity.setId(UUID.randomUUID());
        entity.setFamilyMember(memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId)));
        entity.setPeriodStartDate(periodStartDate);
        entity.setPeriodLength(actualPeriodLength);
        
        // Calculate cycle length based on previous entry
        var previousEntries = repository.findByFamilyMemberIdOrderByPeriodStartDateDesc(memberId);
        if (!previousEntries.isEmpty()) {
            var previousEntry = previousEntries.get(0);
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    previousEntry.getPeriodStartDate(), periodStartDate);
            entity.setCycleLength((int) daysBetween);
        }
        
        var now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        var saved = repository.save(entity);
        return toDomain(saved);
    }

    /**
     * Delete a menstrual cycle entry.
     */
    public void deleteEntry(UUID entryId, UUID requesterId) {
        var entity = repository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Menstrual cycle entry not found: " + entryId));
        
        validateAccess(entity.getFamilyMember().getId(), requesterId);
        
        repository.delete(entity);
    }

    /**
     * Get cycle prediction based on historical data.
     */
    @Transactional(readOnly = true)
    public CyclePrediction getPrediction(UUID memberId, UUID requesterId) {
        validateAccess(memberId, requesterId);
        
        var entries = repository.findByFamilyMemberIdOrderByPeriodStartDateDesc(memberId);
        
        if (entries.isEmpty()) {
            // No data - return default prediction based on standard 28-day cycle
            return createDefaultPrediction();
        }
        
        // Calculate average cycle length
        int avgCycleLength = calculateAverageCycleLength(entries);
        
        // Get last period start date
        var lastPeriodStart = entries.get(0).getPeriodStartDate();
        
        // Calculate average period length
        int avgPeriodLength = calculateAveragePeriodLength(entries);
        
        // Predict next period
        var nextPeriodStart = lastPeriodStart.plusDays(avgCycleLength);
        var nextPeriodEnd = nextPeriodStart.plusDays(avgPeriodLength - 1);
        
        // Predict ovulation (14 days before next period)
        var ovulationDate = nextPeriodStart.minusDays(14);
        
        // Fertile window (5 days before ovulation to 1 day after)
        var fertileWindowStart = ovulationDate.minusDays(5);
        var fertileWindowEnd = ovulationDate.plusDays(1);
        
        // Calculate current cycle day
        var today = LocalDate.now();
        long daysSinceLastPeriod = java.time.temporal.ChronoUnit.DAYS.between(lastPeriodStart, today);
        int currentCycleDay = (int) daysSinceLastPeriod + 1;
        
        // Determine current phase
        String currentPhase = determinePhase(currentCycleDay, avgCycleLength, avgPeriodLength);
        
        return new CyclePrediction(
                nextPeriodStart,
                nextPeriodEnd,
                ovulationDate,
                fertileWindowStart,
                fertileWindowEnd,
                currentCycleDay,
                currentPhase
        );
    }

    private int calculateAverageCycleLength(List<MenstrualCycleEntity> entries) {
        if (entries.size() < 2) {
            return 28; // Default cycle length
        }
        
        // Average of valid cycles (limit to last 6 cycles)
        int cyclesToUse = Math.min(entries.size() - 1, 6);
        long sum = 0;
        int count = 0;
        for (int i = 0; i < entries.size() - 1 && count < cyclesToUse; i++) {
            var current = entries.get(i).getPeriodStartDate();
            var previous = entries.get(i + 1).getPeriodStartDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(previous, current);
            if (days >= 15 && days <= 45) {
                sum += days;
                count++;
            }
        }
        
        return count > 0 ? (int) Math.round((double) sum / count) : 28;
    }

    private int calculateAveragePeriodLength(List<MenstrualCycleEntity> entries) {
        if (entries.isEmpty()) {
            return 5; // Default period length
        }
        
        // Use last 6 entries or all if fewer
        int entriesToUse = Math.min(entries.size(), 6);
        int totalLength = 0;
        int count = 0;
        
        for (int i = 0; i < entriesToUse; i++) {
            Integer length = entries.get(i).getPeriodLength();
            if (length != null && length > 0) {
                totalLength += length;
                count++;
            }
        }
        
        return count > 0 ? Math.round((float) totalLength / count) : 5;
    }

    private String determinePhase(int currentCycleDay, int avgCycleLength, int avgPeriodLength) {
        if (currentCycleDay <= avgPeriodLength) {
            return "menstruation";
        } else if (currentCycleDay <= avgCycleLength - 14) {
            return "follicular";
        } else if (currentCycleDay <= avgCycleLength - 10) {
            return "ovulation";
        } else {
            return "luteal";
        }
    }

    private CyclePrediction createDefaultPrediction() {
        var today = LocalDate.now();
        var nextPeriodStart = today.plusDays(28);
        var nextPeriodEnd = nextPeriodStart.plusDays(4);
        var ovulationDate = nextPeriodStart.minusDays(14);
        var fertileWindowStart = ovulationDate.minusDays(5);
        var fertileWindowEnd = ovulationDate.plusDays(1);
        
        return new CyclePrediction(
                nextPeriodStart,
                nextPeriodEnd,
                ovulationDate,
                fertileWindowStart,
                fertileWindowEnd,
                1,
                "follicular"
        );
    }

    /**
     * Validate that requester has access to view/update menstrual cycle data for the member.
     */
    private void validateAccess(UUID memberId, UUID requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("Requester ID is required");
        }
        
        var member = familyMemberService.getMemberById(memberId);
        var requester = familyMemberService.getMemberById(requesterId);
        
        // Only adults can have menstrual cycle tracking
        if (member.role() != Role.PARENT && member.role() != Role.ASSISTANT) {
            throw new IllegalArgumentException("Menstrual cycle tracking is only available for adults");
        }
        
        // Requester must be in the same family
        if (member.familyId() == null || requester.familyId() == null ||
            !member.familyId().equals(requester.familyId())) {
            throw new IllegalArgumentException("Cannot access menstrual cycle data for member in different family");
        }
        
        // Requester must be PARENT, or ASSISTANT accessing their own data
        boolean isParent = requester.role() == Role.PARENT;
        boolean isAssistantAccessingSelf = requester.role() == Role.ASSISTANT && 
                                           requester.id().equals(memberId);
        
        if (!isParent && !isAssistantAccessingSelf) {
            throw new IllegalArgumentException("Only parents can access menstrual cycle data for others, or assistants can access their own");
        }
        
        // If data is private, only the owner can access it
        if (member.menstrualCyclePrivate() != null && member.menstrualCyclePrivate() && 
            !memberId.equals(requesterId)) {
            throw new IllegalArgumentException("Menstrual cycle data is private and can only be accessed by the owner");
        }
    }

    private MenstrualCycle toDomain(MenstrualCycleEntity entity) {
        return new MenstrualCycle(
                entity.getId(),
                entity.getFamilyMember().getId(),
                entity.getPeriodStartDate(),
                entity.getPeriodLength(),
                entity.getCycleLength(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Cycle prediction data.
     */
    public record CyclePrediction(
            LocalDate nextPeriodStart,
            LocalDate nextPeriodEnd,
            LocalDate ovulationDate,
            LocalDate fertileWindowStart,
            LocalDate fertileWindowEnd,
            int currentCycleDay,
            String currentPhase  // "menstruation", "follicular", "ovulation", "luteal"
    ) {
    }
}
