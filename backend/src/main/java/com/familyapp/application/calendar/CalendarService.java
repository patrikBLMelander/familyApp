package com.familyapp.application.calendar;

import com.familyapp.application.xp.XpService;
import com.familyapp.domain.calendar.CalendarEvent;
import com.familyapp.domain.calendar.CalendarEventCategory;
import com.familyapp.domain.calendar.CalendarEventTaskCompletion;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryEntity;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventEntity;
import com.familyapp.infrastructure.calendar.CalendarEventJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventTaskCompletionEntity;
import com.familyapp.infrastructure.calendar.CalendarEventTaskCompletionJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CalendarService {

    private final CalendarEventJpaRepository eventRepository;
    private final CalendarEventCategoryJpaRepository categoryRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final FamilyJpaRepository familyRepository;
    private final CalendarEventTaskCompletionJpaRepository completionRepository;
    private final XpService xpService;

    public CalendarService(
            CalendarEventJpaRepository eventRepository,
            CalendarEventCategoryJpaRepository categoryRepository,
            FamilyMemberJpaRepository memberRepository,
            FamilyJpaRepository familyRepository,
            CalendarEventTaskCompletionJpaRepository completionRepository,
            XpService xpService
    ) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.completionRepository = completionRepository;
        this.xpService = xpService;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> getEventsForDateRange(UUID familyId, LocalDateTime startDate, LocalDateTime endDate) {
        var baseEvents = eventRepository.findByFamilyIdAndDateRange(familyId, startDate, endDate).stream()
                .map(this::toDomain)
                .toList();
        
        // Get recurring events that might generate instances in this range
        // Optimized: Only get recurring events that could potentially generate instances in the date range
        // (events that start before or during the range, or have no end date)
        var allRecurringEvents = eventRepository.findByFamilyIdOrderByStartDateTimeAsc(familyId).stream()
                .map(this::toDomain)
                .filter(CalendarEvent::isRecurring)
                .filter(event -> {
                    // Only include recurring events that could generate instances in the range
                    var eventStart = event.startDateTime();
                    var eventEndDate = event.recurringEndDate();
                    
                    // Include if:
                    // 1. Event starts before or during the range (could generate instances in range)
                    // 2. Event has no end date (could generate instances indefinitely)
                    // 3. Event's end date is after range start (could still generate instances)
                    return eventStart.isBefore(endDate) || eventStart.isEqual(endDate) ||
                           (eventEndDate == null || eventEndDate.isAfter(startDate.toLocalDate()));
                })
                .toList();
        
        // Filter out recurring base events since we'll generate instances for them
        var nonRecurringBaseEvents = baseEvents.stream()
                .filter(event -> !event.isRecurring())
                .toList();
        
        var result = new java.util.ArrayList<CalendarEvent>(nonRecurringBaseEvents);
        
        // Generate recurring instances only for events that could match the range
        for (var recurringEvent : allRecurringEvents) {
            var instances = generateRecurringInstances(recurringEvent, startDate, endDate);
            result.addAll(instances);
        }
        
        return result.stream()
                .sorted((a, b) -> a.startDateTime().compareTo(b.startDateTime()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> getAllEvents(UUID familyId) {
        // Optimized: Use reasonable default range (3 months) instead of 2 years
        // This prevents generating thousands of recurring instances in memory
        var now = LocalDateTime.now();
        var defaultEndDate = now.plusMonths(3); // 3 months ahead is reasonable for most use cases
        
        // Reuse getEventsForDateRange with default range
        return getEventsForDateRange(familyId, now, defaultEndDate);
    }
    
    private List<CalendarEvent> generateRecurringInstances(
            CalendarEvent baseEvent,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        var instances = new java.util.ArrayList<CalendarEvent>();
        var recurringType = baseEvent.recurringType();
        var interval = baseEvent.recurringInterval() != null ? baseEvent.recurringInterval() : 1;
        var endDate = baseEvent.recurringEndDate();
        var endCount = baseEvent.recurringEndCount();
        
        if (recurringType == null) {
            return instances;
        }
        
        var currentStart = baseEvent.startDateTime();
        var currentEnd = baseEvent.endDateTime();
        var duration = currentEnd != null 
                ? java.time.Duration.between(currentStart, currentEnd)
                : java.time.Duration.ZERO;
        
        int count = 0;
        int maxIterations = 1000; // Safety limit
        int iteration = 0;
        
        // If the base event starts before rangeStart, skip to the first occurrence >= rangeStart
        // Otherwise, start from the base event's start time
        if (currentStart.isBefore(rangeStart)) {
            // Skip past occurrences until we reach rangeStart
            while (currentStart.isBefore(rangeStart) && iteration < maxIterations) {
                currentStart = getNextOccurrence(currentStart, recurringType, interval);
                if (currentEnd != null) {
                    currentEnd = currentStart.plus(duration);
                }
                iteration++;
            }
        }
        
        // Generate instances within the range
        while ((currentStart.isBefore(rangeEnd) || currentStart.isEqual(rangeEnd)) && iteration < maxIterations) {
            // Check end conditions
            if (endDate != null && currentStart.toLocalDate().isAfter(endDate)) {
                break;
            }
            if (endCount != null && count >= endCount) {
                break;
            }
            
            // Only add if within range (>= rangeStart and <= rangeEnd)
            if (!currentStart.isBefore(rangeStart) && !currentStart.isAfter(rangeEnd)) {
                // Create instance
                instances.add(new CalendarEvent(
                        baseEvent.id(),
                        baseEvent.familyId(),
                        baseEvent.categoryId(),
                        baseEvent.title(),
                        baseEvent.description(),
                        currentStart,
                        currentEnd,
                        baseEvent.isAllDay(),
                        baseEvent.location(),
                        baseEvent.createdById(),
                        null, // Recurring type is null for instances
                        null,
                        null,
                        null,
                        baseEvent.isTask(),
                        baseEvent.xpPoints(),
                        baseEvent.isRequired(),
                        baseEvent.createdAt(),
                        baseEvent.updatedAt(),
                        baseEvent.participantIds()
                ));
                
                count++;
            }
            
            iteration++;
            
            // Move to next occurrence
            currentStart = getNextOccurrence(currentStart, recurringType, interval);
            if (currentEnd != null) {
                currentEnd = currentStart.plus(duration);
            }
        }
        
        return instances;
    }
    
    private LocalDateTime getNextOccurrence(
            LocalDateTime current,
            CalendarEvent.RecurringType type,
            int interval
    ) {
        return switch (type) {
            case DAILY -> current.plusDays(interval);
            case WEEKLY -> current.plusWeeks(interval);
            case MONTHLY -> current.plusMonths(interval);
            case YEARLY -> current.plusYears(interval);
        };
    }

    private LocalDate getNextOccurrenceDate(
            LocalDate current,
            CalendarEvent.RecurringType type,
            int interval
    ) {
        return switch (type) {
            case DAILY -> current.plusDays(interval);
            case WEEKLY -> current.plusWeeks(interval);
            case MONTHLY -> current.plusMonths(interval);
            case YEARLY -> current.plusYears(interval);
        };
    }

    public CalendarEvent createEvent(
            UUID familyId,
            UUID categoryId,
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean isAllDay,
            String location,
            UUID createdById,
            Set<UUID> participantIds,
            CalendarEvent.RecurringType recurringType,
            Integer recurringInterval,
            java.time.LocalDate recurringEndDate,
            Integer recurringEndCount,
            Boolean isTask,
            Integer xpPoints,
            Boolean isRequired
    ) {
        var now = OffsetDateTime.now();
        var entity = new CalendarEventEntity();
        entity.setId(UUID.randomUUID());
        
        if (familyId != null) {
            familyRepository.findById(familyId).ifPresent(entity::setFamily);
        }
        
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(entity::setCategory);
        }
        
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setStartDateTime(startDateTime);
        entity.setEndDateTime(endDateTime);
        entity.setAllDay(isAllDay);
        entity.setLocation(location);
        
        if (createdById != null) {
            memberRepository.findById(createdById).ifPresent(entity::setCreatedBy);
        }
        
        // Set participants
        if (participantIds != null && !participantIds.isEmpty()) {
            var participants = participantIds.stream()
                    .map(memberRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
            entity.setParticipants(participants);
        }
        
        // Recurring fields
        if (recurringType != null) {
            entity.setRecurringType(recurringType.name());
            entity.setRecurringInterval(recurringInterval != null ? recurringInterval : 1);
            entity.setRecurringEndDate(recurringEndDate);
            entity.setRecurringEndCount(recurringEndCount);
        } else {
            entity.setRecurringType(null);
            entity.setRecurringInterval(null);
            entity.setRecurringEndDate(null);
            entity.setRecurringEndCount(null);
        }
        
        // Task fields
        boolean isTaskValue = isTask != null ? isTask : false;
        entity.setTask(isTaskValue);
        
        // Validate: If isTask=false, xpPoints should be null or 0
        // If isTask=true and xpPoints is null, use default of 1
        if (!isTaskValue && xpPoints != null && xpPoints > 0) {
            throw new IllegalArgumentException("xpPoints can only be set when isTask=true");
        }
        if (isTaskValue && xpPoints == null) {
            entity.setXpPoints(1); // Default XP for tasks
        } else {
            entity.setXpPoints(xpPoints);
        }
        
        entity.setRequired(isRequired != null ? isRequired : true);
        
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        var saved = eventRepository.save(entity);
        return toDomain(saved);
    }

    public CalendarEvent updateEvent(
            UUID eventId,
            UUID categoryId,
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean isAllDay,
            String location,
            Set<UUID> participantIds,
            CalendarEvent.RecurringType recurringType,
            Integer recurringInterval,
            java.time.LocalDate recurringEndDate,
            Integer recurringEndCount,
            Boolean isTask,
            Integer xpPoints,
            Boolean isRequired
    ) {
        var entity = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));
        
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(entity::setCategory);
        } else {
            entity.setCategory(null);
        }
        
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setStartDateTime(startDateTime);
        entity.setEndDateTime(endDateTime);
        entity.setAllDay(isAllDay);
        entity.setLocation(location);
        
        // Update participants
        if (participantIds != null) {
            var participants = participantIds.stream()
                    .map(memberRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
            entity.setParticipants(participants);
        }
        
        // Update recurring fields
        if (recurringType != null) {
            entity.setRecurringType(recurringType.name());
            entity.setRecurringInterval(recurringInterval != null ? recurringInterval : 1);
            entity.setRecurringEndDate(recurringEndDate);
            entity.setRecurringEndCount(recurringEndCount);
        } else {
            entity.setRecurringType(null);
            entity.setRecurringInterval(null);
            entity.setRecurringEndDate(null);
            entity.setRecurringEndCount(null);
        }
        
        // Update task fields (only if provided)
        if (isTask != null) {
            entity.setTask(isTask);
            // Validate: If isTask=false, xpPoints should be null or 0
            if (!isTask && entity.getXpPoints() != null && entity.getXpPoints() > 0) {
                entity.setXpPoints(null);
            }
            // If isTask=true and xpPoints is null, use default of 1
            if (isTask && (xpPoints == null && entity.getXpPoints() == null)) {
                entity.setXpPoints(1);
            }
        }
        if (xpPoints != null) {
            // Validate: xpPoints can only be set when isTask=true
            boolean currentIsTask = isTask != null ? isTask : entity.isTask();
            if (!currentIsTask && xpPoints > 0) {
                throw new IllegalArgumentException("xpPoints can only be set when isTask=true");
            }
            entity.setXpPoints(xpPoints);
        }
        if (isRequired != null) {
            entity.setRequired(isRequired);
        }
        
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = eventRepository.save(entity);
        return toDomain(saved);
    }

    public void deleteEvent(UUID eventId) {
        eventRepository.deleteById(eventId);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventCategory> getAllCategories(UUID familyId) {
        return categoryRepository.findByFamilyIdOrderByNameAsc(familyId).stream()
                .map(this::toDomainCategory)
                .toList();
    }

    public CalendarEventCategory createCategory(UUID familyId, String name, String color) {
        var now = OffsetDateTime.now();
        var entity = new CalendarEventCategoryEntity();
        entity.setId(UUID.randomUUID());
        
        if (familyId != null) {
            familyRepository.findById(familyId).ifPresent(entity::setFamily);
        }
        
        entity.setName(name);
        entity.setColor(color != null ? color : "#b8e6b8");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        var saved = categoryRepository.save(entity);
        return toDomainCategory(saved);
    }

    public CalendarEventCategory updateCategory(UUID categoryId, String name, String color) {
        var entity = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        
        entity.setName(name);
        if (color != null) {
            entity.setColor(color);
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = categoryRepository.save(entity);
        return toDomainCategory(saved);
    }

    public void deleteCategory(UUID categoryId) {
        categoryRepository.deleteById(categoryId);
    }

    private CalendarEvent toDomain(CalendarEventEntity entity) {
        var participantIds = entity.getParticipants().stream()
                .map(participant -> participant.getId())
                .collect(Collectors.toSet());
        
        CalendarEvent.RecurringType recurringType = null;
        if (entity.getRecurringType() != null) {
            try {
                recurringType = CalendarEvent.RecurringType.valueOf(entity.getRecurringType());
            } catch (IllegalArgumentException e) {
                // Invalid recurring type, leave as null
            }
        }
        
        return new CalendarEvent(
                entity.getId(),
                entity.getFamily() != null ? entity.getFamily().getId() : null,
                entity.getCategory() != null ? entity.getCategory().getId() : null,
                entity.getTitle(),
                entity.getDescription(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.isAllDay(),
                entity.getLocation(),
                entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null,
                recurringType,
                entity.getRecurringInterval(),
                entity.getRecurringEndDate(),
                entity.getRecurringEndCount(),
                entity.isTask(),
                entity.getXpPoints(),
                entity.isRequired(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                participantIds
        );
    }

    private CalendarEventCategory toDomainCategory(CalendarEventCategoryEntity entity) {
        return new CalendarEventCategory(
                entity.getId(),
                entity.getFamily() != null ? entity.getFamily().getId() : null,
                entity.getName(),
                entity.getColor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // Task Completion Methods

    /**
     * Mark a task (calendar event with isTask=true) as completed for a specific member and occurrence date.
     * For events with multiple participants, completion is shared (when one participant marks it complete, it's complete for all).
     */
    public CalendarEventTaskCompletion markTaskCompleted(UUID eventId, UUID memberId, LocalDate occurrenceDate) {
        var eventEntity = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));
        
        if (!eventEntity.isTask()) {
            throw new IllegalArgumentException("Event is not a task (isTask=false)");
        }
        
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        
        // Validate: Member must be in the same family as the event
        if (eventEntity.getFamily() == null || member.getFamily() == null ||
            !eventEntity.getFamily().getId().equals(member.getFamily().getId())) {
            throw new IllegalArgumentException("Member is not in the same family as the event");
        }
        
        // Validate occurrence date
        validateOccurrenceDate(eventEntity, occurrenceDate);
        
        // Check if already completed by this member
        var existing = completionRepository.findByEventIdAndMemberIdAndOccurrenceDate(eventId, memberId, occurrenceDate);
        if (existing.isPresent()) {
            return toDomainCompletion(existing.get());
        }
        
        // Create completion
        var completionEntity = new CalendarEventTaskCompletionEntity();
        completionEntity.setId(UUID.randomUUID());
        completionEntity.setEvent(eventEntity);
        completionEntity.setMember(member);
        completionEntity.setOccurrenceDate(occurrenceDate);
        completionEntity.setCompletedAt(OffsetDateTime.now());
        
        var saved = completionRepository.save(completionEntity);
        
        // Award XP if xpPoints is set (only for children, handled by XpService)
        Integer xpPoints = eventEntity.getXpPoints();
        if (xpPoints != null && xpPoints > 0) {
            xpService.awardXp(memberId, xpPoints);
        }
        
        return toDomainCompletion(saved);
    }

    /**
     * Remove completion for a task.
     */
    public void unmarkTaskCompleted(UUID eventId, UUID memberId, LocalDate occurrenceDate) {
        var eventEntity = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));
        
        var completion = completionRepository.findByEventIdAndMemberIdAndOccurrenceDate(eventId, memberId, occurrenceDate);
        if (completion.isPresent()) {
            // Remove XP if xpPoints is set (only for children, handled by XpService)
            Integer xpPoints = eventEntity.getXpPoints();
            if (xpPoints != null && xpPoints > 0) {
                xpService.removeXp(memberId, xpPoints);
            }
            completionRepository.delete(completion.get());
        }
    }

    /**
     * Check if a task is completed for a specific occurrence date.
     * For events with multiple participants, returns true if ANY participant has marked it complete.
     */
    @Transactional(readOnly = true)
    public boolean isTaskCompleted(UUID eventId, LocalDate occurrenceDate) {
        var completions = completionRepository.findByEventIdAndOccurrenceDate(eventId, occurrenceDate);
        return !completions.isEmpty();
    }

    /**
     * Get all completions for a specific event.
     */
    @Transactional(readOnly = true)
    public List<CalendarEventTaskCompletion> getTaskCompletions(UUID eventId) {
        return completionRepository.findByEventId(eventId).stream()
                .map(this::toDomainCompletion)
                .toList();
    }

    /**
     * Get completions for a specific member.
     */
    @Transactional(readOnly = true)
    public List<CalendarEventTaskCompletion> getTaskCompletionsForMember(UUID memberId) {
        return completionRepository.findByMemberId(memberId).stream()
                .map(this::toDomainCompletion)
                .toList();
    }

    private CalendarEventTaskCompletion toDomainCompletion(CalendarEventTaskCompletionEntity entity) {
        return new CalendarEventTaskCompletion(
                entity.getId(),
                entity.getEvent() != null ? entity.getEvent().getId() : null,
                entity.getMember() != null ? entity.getMember().getId() : null,
                entity.getOccurrenceDate(),
                entity.getCompletedAt()
        );
    }

    /**
     * Validate that the occurrence date matches the event's schedule.
     * For one-time events: occurrenceDate must equal the start date.
     * For recurring events: occurrenceDate must be a valid occurrence date.
     */
    private void validateOccurrenceDate(CalendarEventEntity eventEntity, LocalDate occurrenceDate) {
        if (occurrenceDate == null) {
            throw new IllegalArgumentException("Occurrence date cannot be null");
        }
        
        LocalDate eventStartDate = eventEntity.getStartDateTime().toLocalDate();
        
        // For one-time events (not recurring)
        if (eventEntity.getRecurringType() == null || eventEntity.getRecurringType().isEmpty()) {
            if (!occurrenceDate.equals(eventStartDate)) {
                throw new IllegalArgumentException(
                    "Occurrence date " + occurrenceDate + " does not match event start date " + eventStartDate
                );
            }
            return;
        }
        
        // For recurring events: check if occurrenceDate is a valid occurrence
        // We check if the date matches the recurring pattern
        CalendarEvent.RecurringType recurringType;
        try {
            recurringType = CalendarEvent.RecurringType.valueOf(eventEntity.getRecurringType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid recurring type: " + eventEntity.getRecurringType());
        }
        
        int interval = eventEntity.getRecurringInterval() != null ? eventEntity.getRecurringInterval() : 1;
        
        // Check recurring end date
        if (eventEntity.getRecurringEndDate() != null && occurrenceDate.isAfter(eventEntity.getRecurringEndDate())) {
            throw new IllegalArgumentException(
                "Occurrence date " + occurrenceDate + " is after recurring end date " + eventEntity.getRecurringEndDate()
            );
        }
        
        // Check if occurrenceDate matches the pattern
        boolean isValid = false;
        LocalDate currentDate = eventStartDate;
        int maxIterations = 10000; // Safety limit
        int iteration = 0;
        
        while (!currentDate.isAfter(occurrenceDate) && iteration < maxIterations) {
            if (currentDate.equals(occurrenceDate)) {
                isValid = true;
                break;
            }
            
            // Move to next occurrence
            currentDate = getNextOccurrenceDate(currentDate, recurringType, interval);
            
            iteration++;
            
            // Check recurring end date
            if (eventEntity.getRecurringEndDate() != null && currentDate.isAfter(eventEntity.getRecurringEndDate())) {
                break;
            }
        }
        
        if (!isValid) {
            throw new IllegalArgumentException(
                "Occurrence date " + occurrenceDate + " does not match the recurring pattern for event starting " + eventStartDate
            );
        }
    }
}

