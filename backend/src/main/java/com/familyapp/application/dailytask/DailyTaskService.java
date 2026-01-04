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
        
        var allTasks = taskRepository.findByDayOfWeek(dayOfWeek);
        
        // Filter tasks that apply to this member and family
        var tasks = allTasks.stream()
                .filter(task -> {
                    // Filter by family - if familyId is provided, task must belong to that family
                    // If task has no family set, skip it (legacy data or invalid)
                    if (familyId != null) {
                        if (task.getFamily() == null) {
                            return false; // Task has no family, skip it
                        }
                        if (!task.getFamily().getId().equals(familyId)) {
                            return false; // Task belongs to different family
                        }
                    } else {
                        // No familyId provided, but we should have one for proper filtering
                        // Skip tasks without family to avoid showing wrong data
                        if (task.getFamily() == null) {
                            return false;
                        }
                    }
                    
                    // If task has no members assigned, it applies to all members in the family
                    if (task.getMembers().isEmpty()) {
                        return true;
                    }
                    
                    // Otherwise, check if this specific member is in the assigned members list
                    if (memberId == null) {
                        return false; // No memberId provided, can't match
                    }
                    
                    return task.getMembers().stream()
                            .anyMatch(m -> m.getId().equals(memberId));
                })
                .toList();
        
        // Get completions for these tasks for this specific member
        var taskIds = tasks.stream().map(DailyTaskEntity::getId).collect(Collectors.toSet());
        var completions = completionRepository.findByCompletedDate(today).stream()
                .filter(c -> taskIds.contains(c.getTask().getId()))
                .filter(c -> memberId == null || (c.getMember() != null && c.getMember().getId().equals(memberId)))
                .collect(Collectors.toMap(
                        c -> c.getTask().getId(),
                        c -> true
                ));
        
        return tasks.stream()
                .map(task -> {
                    boolean completed = completions.containsKey(task.getId());
                    return new DailyTaskWithCompletion(
                            toDomain(task),
                            completed
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyTaskWithChildrenCompletion> getTasksForTodayWithChildren(UUID parentId, UUID familyId) {
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().name();
        
        // Filter tasks by family
        var allTasks = taskRepository.findByDayOfWeek(dayOfWeek).stream()
                .filter(task -> familyId == null || (task.getFamily() != null && task.getFamily().getId().equals(familyId)))
                .toList();
        
        // Get all children in the family (members with role CHILD)
        var allChildren = memberRepository.findAll().stream()
                .filter(m -> familyId == null || (m.getFamily() != null && m.getFamily().getId().equals(familyId)))
                .filter(m -> "CHILD".equals(m.getRole()))
                .toList();
        
        // Get all completions for today (filtered by family)
        var allCompletions = completionRepository.findByCompletedDate(today).stream()
                .filter(c -> {
                    if (familyId == null) return true;
                    if (c.getMember() == null || c.getMember().getFamily() == null) return false;
                    return c.getMember().getFamily().getId().equals(familyId);
                })
                .toList();
        
        var result = new java.util.ArrayList<DailyTaskWithChildrenCompletion>();
        
        // Get all parents in the family for checking parent tasks
        var allParents = memberRepository.findAll().stream()
                .filter(m -> familyId == null || (m.getFamily() != null && m.getFamily().getId().equals(familyId)))
                .filter(m -> "PARENT".equals(m.getRole()))
                .toList();
        
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

    @Transactional(readOnly = true)
    public List<DailyTask> getAllTasks(UUID familyId) {
        return taskRepository.findAllByOrderByPositionAsc().stream()
                .filter(task -> familyId == null || (task.getFamily() != null && task.getFamily().getId().equals(familyId)))
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
        
        var existing = taskRepository.findAll();
        var maxPosition = existing.stream()
                .mapToInt(DailyTaskEntity::getPosition)
                .max()
                .orElse(-1);
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
        
        // Find existing completion for this specific task, date, and member
        var existing = completionRepository.findByTaskIdAndCompletedDateAndMemberId(taskId, today, memberId);
        
        if (existing.isPresent()) {
            // Delete the completion (uncheck)
            var completion = existing.get();
            var task = completion.getTask();
            // Remove XP when unchecking
            xpService.removeXp(memberId, task.getXpPoints());
            completionRepository.delete(completion);
        } else {
            // Create a new completion (check)
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
            
            // Award XP when checking (only for children)
            xpService.awardXp(memberId, task.getXpPoints());
        }
    }

    public List<DailyTask> reorderTasks(List<UUID> orderedTaskIds) {
        var tasks = taskRepository.findAll();
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

