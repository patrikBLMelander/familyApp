package com.familyapp.application.dailytask;

import com.familyapp.domain.dailytask.DailyTask;
import com.familyapp.infrastructure.dailytask.DailyTaskCompletionEntity;
import com.familyapp.infrastructure.dailytask.DailyTaskCompletionJpaRepository;
import com.familyapp.infrastructure.dailytask.DailyTaskEntity;
import com.familyapp.infrastructure.dailytask.DailyTaskJpaRepository;
import com.familyapp.application.xp.XpService;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyTaskService {

    private final DailyTaskJpaRepository taskRepository;
    private final DailyTaskCompletionJpaRepository completionRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final FamilyJpaRepository familyRepository;
    private final XpService xpService;

    public DailyTaskService(
            DailyTaskJpaRepository taskRepository,
            DailyTaskCompletionJpaRepository completionRepository,
            FamilyMemberJpaRepository memberRepository,
            FamilyJpaRepository familyRepository,
            XpService xpService
    ) {
        this.taskRepository = taskRepository;
        this.completionRepository = completionRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.xpService = xpService;
    }

    @Transactional(readOnly = true)
    public List<DailyTaskWithCompletion> getTasksForToday(UUID memberId, UUID familyId) {
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().name();

        // Family-scoped query — no in-memory cross-family filtering needed
        var familyTasks = familyId != null
                ? taskRepository.findByDayOfWeekAndFamilyId(dayOfWeek, familyId)
                : List.<DailyTaskEntity>of();

        var tasks = familyTasks.stream()
                .filter(task -> {
                    if (task.getMembers().isEmpty()) {
                        return true; // applies to all family members
                    }
                    if (memberId == null) {
                        return false;
                    }
                    return task.getMembers().stream().anyMatch(m -> m.getId().equals(memberId));
                })
                .toList();

        // Family-scoped completions query — replaces the global findByCompletedDate
        var taskIds = tasks.stream().map(DailyTaskEntity::getId).collect(Collectors.toSet());
        List<DailyTaskCompletionEntity> rawCompletions = familyId != null
                ? completionRepository.findByCompletedDateAndFamilyId(today, familyId)
                : List.of();
        var completions = rawCompletions.stream()
                .filter(c -> taskIds.contains(c.getTask().getId()))
                .filter(c -> memberId == null || (c.getMember() != null && c.getMember().getId().equals(memberId)))
                .collect(Collectors.toMap(c -> c.getTask().getId(), c -> true));

        return tasks.stream()
                .map(task -> new DailyTaskWithCompletion(toDomain(task), completions.containsKey(task.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyTaskWithChildrenCompletion> getTasksForTodayWithChildren(UUID parentId, UUID familyId) {
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().name();
        
        // Family-scoped query — no in-memory cross-family filtering needed
        var allTasks = familyId != null
                ? taskRepository.findByDayOfWeekAndFamilyId(dayOfWeek, familyId)
                : List.<DailyTaskEntity>of();
        
        // Get all children in the family (members with role CHILD)
        // Optimized: Use query instead of fetching all and filtering in memory
        List<com.familyapp.infrastructure.familymember.FamilyMemberEntity> allChildren;
        if (familyId != null) {
            allChildren = memberRepository.findByFamilyIdAndRole(familyId, "CHILD");
        } else {
            // Fallback for legacy code (should not happen in production)
            allChildren = memberRepository.findByRole("CHILD");
        }
        
        // Get all completions for today (filtered by family)
        // Optimized: Query completions by family if possible
        List<com.familyapp.infrastructure.dailytask.DailyTaskCompletionEntity> allCompletions;
        if (familyId != null) {
            allCompletions = completionRepository.findByCompletedDateAndFamilyId(today, familyId);
        } else {
            // Fallback: fetch all and filter (should not happen in production)
            allCompletions = completionRepository.findByCompletedDate(today);
        }
        
        var result = new java.util.ArrayList<DailyTaskWithChildrenCompletion>();
        
        // Get all parents in the family for checking parent tasks
        // Optimized: Use query instead of fetching all and filtering in memory
        List<com.familyapp.infrastructure.familymember.FamilyMemberEntity> allParents;
        if (familyId != null) {
            allParents = memberRepository.findByFamilyIdAndRole(familyId, "PARENT");
        } else {
            // Fallback for legacy code (should not happen in production)
            allParents = memberRepository.findByRole("PARENT");
        }
        
        for (var task : allTasks) {
            boolean appliesToAll = task.getMembers().isEmpty();
            
            // Get all members (both parents and children) that this task applies to
            var applicableMembers = new java.util.ArrayList<com.familyapp.infrastructure.familymember.FamilyMemberEntity>();
            
            if (appliesToAll) {
                // Task applies to all - include all family members
                applicableMembers.addAll(allParents);
                applicableMembers.addAll(allChildren);
            } else {
                // Task applies to specific members - filter by task assignments
                for (var assignedMember : task.getMembers()) {
                    if (familyId == null || (assignedMember.getFamily() != null && assignedMember.getFamily().getId().equals(familyId))) {
                        applicableMembers.add(assignedMember);
                    }
                }
            }
            
            // Get completions for all applicable members
            var memberCompletions = applicableMembers.stream()
                    .map(member -> {
                        var completion = allCompletions.stream()
                                .filter(c -> c.getTask().getId().equals(task.getId()) &&
                                        c.getMember() != null &&
                                        c.getMember().getId().equals(member.getId()))
                                .findFirst();
                        return new ChildCompletion(member.getId(), member.getName(), completion.isPresent());
                    })
                    .toList();
            
            // Only add task if it has applicable members
            if (!memberCompletions.isEmpty()) {
                result.add(new DailyTaskWithChildrenCompletion(
                        toDomain(task),
                        memberCompletions
                ));
            }
        }
        
        return result;
    }
    
    public record ChildCompletion(UUID childId, String childName, boolean completed) {
    }
    
    public record DailyTaskWithChildrenCompletion(DailyTask task, List<ChildCompletion> childCompletions) {
    }

    /** Returns true iff the task exists and belongs to the given family. O(1) indexed check. */
    @Transactional(readOnly = true)
    public boolean taskBelongsToFamily(UUID taskId, UUID familyId) {
        return taskRepository.existsByIdAndFamilyId(taskId, familyId);
    }

    @Transactional(readOnly = true)
    public List<DailyTask> getAllTasks(UUID familyId) {
        // Optimized: Query by family ID instead of fetching all and filtering
        List<DailyTaskEntity> tasks;
        if (familyId != null) {
            tasks = taskRepository.findByFamilyIdOrderByPositionAsc(familyId);
        } else {
            // Fallback for legacy code (should not happen in production)
            tasks = taskRepository.findAllByOrderByPositionAsc();
        }
        return tasks.stream()
                .map(this::toDomain)
                .toList();
    }

    public DailyTask createTask(String name, String description, Set<DailyTask.DayOfWeek> daysOfWeek, Set<UUID> memberIds, UUID familyId, boolean isRequired, int xpPoints) {
        var now = OffsetDateTime.now();
        var entity = new DailyTaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setDescription(description);
        entity.setMonday(daysOfWeek.contains(DailyTask.DayOfWeek.MONDAY));
        entity.setTuesday(daysOfWeek.contains(DailyTask.DayOfWeek.TUESDAY));
        entity.setWednesday(daysOfWeek.contains(DailyTask.DayOfWeek.WEDNESDAY));
        entity.setThursday(daysOfWeek.contains(DailyTask.DayOfWeek.THURSDAY));
        entity.setFriday(daysOfWeek.contains(DailyTask.DayOfWeek.FRIDAY));
        entity.setSaturday(daysOfWeek.contains(DailyTask.DayOfWeek.SATURDAY));
        entity.setSunday(daysOfWeek.contains(DailyTask.DayOfWeek.SUNDAY));
        
        // Set family
        if (familyId != null) {
            familyRepository.findById(familyId).ifPresent(entity::setFamily);
        }
        
        // Set members if provided
        if (memberIds != null && !memberIds.isEmpty()) {
            var members = memberRepository.findAllById(memberIds);
            entity.setMembers(members);
        }
        
        // Optimized: Query max position directly instead of fetching all tasks
        Integer maxPosition;
        if (familyId != null) {
            maxPosition = taskRepository.findMaxPositionByFamilyId(familyId);
            if (maxPosition == null) {
                maxPosition = -1;
            }
        } else {
            // Fallback for legacy code (should not happen in production)
            var existing = taskRepository.findAll();
            maxPosition = existing.stream()
                    .mapToInt(DailyTaskEntity::getPosition)
                    .max()
                    .orElse(-1);
        }
        entity.setPosition(maxPosition + 1);
        entity.setRequired(isRequired);
        entity.setXpPoints(xpPoints);
        
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        var saved = taskRepository.save(entity);
        return toDomain(saved);
    }

    public DailyTask updateTask(UUID taskId, String name, String description, Set<DailyTask.DayOfWeek> daysOfWeek, Set<UUID> memberIds, boolean isRequired, int xpPoints) {
        var entity = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Daily task not found: " + taskId));
        
        entity.setName(name);
        entity.setDescription(description);
        entity.setMonday(daysOfWeek.contains(DailyTask.DayOfWeek.MONDAY));
        entity.setTuesday(daysOfWeek.contains(DailyTask.DayOfWeek.TUESDAY));
        entity.setWednesday(daysOfWeek.contains(DailyTask.DayOfWeek.WEDNESDAY));
        entity.setThursday(daysOfWeek.contains(DailyTask.DayOfWeek.THURSDAY));
        entity.setFriday(daysOfWeek.contains(DailyTask.DayOfWeek.FRIDAY));
        entity.setSaturday(daysOfWeek.contains(DailyTask.DayOfWeek.SATURDAY));
        entity.setSunday(daysOfWeek.contains(DailyTask.DayOfWeek.SUNDAY));
        
        // Update members
        if (memberIds == null || memberIds.isEmpty()) {
            entity.setMembers(new java.util.ArrayList<>());
        } else {
            var members = memberRepository.findAllById(memberIds);
            entity.setMembers(members);
        }
        
        entity.setRequired(isRequired);
        entity.setXpPoints(xpPoints);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = taskRepository.save(entity);
        return toDomain(saved);
    }

    public void deleteTask(UUID taskId) {
        taskRepository.deleteById(taskId);
    }

    public void toggleTaskCompletion(UUID taskId, UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID is required to toggle task completion");
        }

        LocalDate today = LocalDate.now();

        // SELECT … FOR UPDATE serializes concurrent toggles for the same task/member/date.
        // Without this, two rapid requests could both see "no completion" and both insert,
        // causing double-XP or a constraint violation.
        var existing = completionRepository.findByTaskIdAndCompletedDateAndMemberIdForUpdate(taskId, today, memberId);

        if (existing.isPresent()) {
            var completion = existing.get();
            var task = completion.getTask();
            xpService.removeXp(memberId, task.getXpPoints());
            completionRepository.delete(completion);
        } else {
            var task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Daily task not found: " + taskId));

            var member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));

            var completion = new DailyTaskCompletionEntity();
            completion.setId(UUID.randomUUID());
            completion.setTask(task);
            completion.setMember(member);
            completion.setCompletedDate(today);
            completion.setCompletedAt(OffsetDateTime.now());

            completionRepository.save(completion);
            xpService.awardXp(memberId, task.getXpPoints());
        }
    }

    public List<DailyTask> reorderTasks(List<UUID> orderedTaskIds) {
        // Optimized: Only fetch the tasks we need, not all tasks
        var tasks = taskRepository.findAllById(orderedTaskIds);
        var idToTask = tasks.stream()
                .collect(Collectors.toMap(DailyTaskEntity::getId, t -> t));
        
        int position = 0;
        for (UUID id : orderedTaskIds) {
            var task = idToTask.get(id);
            if (task != null) {
                task.setPosition(position++);
            }
        }
        
        var saved = taskRepository.saveAll(tasks);
        return saved.stream()
                .map(this::toDomain)
                .sorted((a, b) -> Integer.compare(a.position(), b.position()))
                .toList();
    }

    private DailyTask toDomain(DailyTaskEntity entity) {
        Set<DailyTask.DayOfWeek> daysOfWeek = new java.util.HashSet<>();
        if (entity.isMonday()) daysOfWeek.add(DailyTask.DayOfWeek.MONDAY);
        if (entity.isTuesday()) daysOfWeek.add(DailyTask.DayOfWeek.TUESDAY);
        if (entity.isWednesday()) daysOfWeek.add(DailyTask.DayOfWeek.WEDNESDAY);
        if (entity.isThursday()) daysOfWeek.add(DailyTask.DayOfWeek.THURSDAY);
        if (entity.isFriday()) daysOfWeek.add(DailyTask.DayOfWeek.FRIDAY);
        if (entity.isSaturday()) daysOfWeek.add(DailyTask.DayOfWeek.SATURDAY);
        if (entity.isSunday()) daysOfWeek.add(DailyTask.DayOfWeek.SUNDAY);
        
        Set<UUID> memberIds = entity.getMembers().stream()
                .map(FamilyMemberEntity::getId)
                .collect(Collectors.toSet());
        
        return new DailyTask(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                daysOfWeek,
                memberIds,
                entity.getPosition(),
                entity.getFamily() != null ? entity.getFamily().getId() : null,
                entity.isRequired(),
                entity.getXpPoints(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record DailyTaskWithCompletion(DailyTask task, boolean completed) {
    }
}

