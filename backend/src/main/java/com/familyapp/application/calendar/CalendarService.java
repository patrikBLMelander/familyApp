package com.familyapp.application.calendar;

import com.familyapp.application.xp.XpService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.pet.CollectedFoodService;
import com.familyapp.domain.calendar.CalendarEvent;
import com.familyapp.domain.calendar.CalendarEventCategory;
import com.familyapp.domain.calendar.CalendarEventTaskCompletion;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryEntity;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventEntity;
import com.familyapp.infrastructure.calendar.CalendarEventJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventTaskCompletionEntity;
import com.familyapp.application.cache.CacheService;
import com.familyapp.infrastructure.calendar.CalendarEventTaskCompletionJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventExceptionEntity;
import com.familyapp.infrastructure.calendar.CalendarEventExceptionJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final FamilyMemberService memberService;
    private final FamilyJpaRepository familyRepository;
    private final CalendarEventTaskCompletionJpaRepository completionRepository;
    private final CalendarEventExceptionJpaRepository exceptionRepository;
    private final XpService xpService;
    private final CacheService cacheService;
    private final CollectedFoodService foodService;

    public CalendarService(
            CalendarEventJpaRepository eventRepository,
            CalendarEventCategoryJpaRepository categoryRepository,
            FamilyMemberJpaRepository memberRepository,
            FamilyMemberService memberService,
            FamilyJpaRepository familyRepository,
            CalendarEventTaskCompletionJpaRepository completionRepository,
            CalendarEventExceptionJpaRepository exceptionRepository,
            XpService xpService,
            CacheService cacheService,
            CollectedFoodService foodService
    ) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.familyRepository = familyRepository;
        this.foodService = foodService;
        this.completionRepository = completionRepository;
        this.exceptionRepository = exceptionRepository;
        this.xpService = xpService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> getEventsForDateRange(UUID familyId, LocalDateTime startDate, LocalDateTime endDate) {
        var baseEvents = eventRepository.findByFamilyIdAndDateRange(familyId, startDate, endDate).stream()
                .map(this::toDomain)
                .toList();
        
        // Get all modified event IDs from exceptions (to filter them out from baseEvents to avoid duplicates)
        var allExceptionEntities = exceptionRepository.findAll();
        var modifiedEventIds = allExceptionEntities.stream()
                .filter(e -> e.getModifiedEvent() != null)
                .map(e -> e.getModifiedEvent().getId())
                .collect(java.util.stream.Collectors.toSet());
        
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
        // Also filter out modified events (they'll be added via exceptions)
        var nonRecurringBaseEvents = baseEvents.stream()
                .filter(event -> !event.isRecurring() && !modifiedEventIds.contains(event.id()))
                .toList();
        
        var result = new java.util.ArrayList<CalendarEvent>(nonRecurringBaseEvents);
        
        // Batch fetch all exceptions to avoid N+1 query problem
        // If there are no recurring events, skip the batch fetch
        if (!allRecurringEvents.isEmpty()) {
            var recurringEventIds = allRecurringEvents.stream()
                    .map(CalendarEvent::id)
                    .toList();
            
            // Batch fetch all exceptions for all recurring events in one query
            var allExceptions = exceptionRepository.findByEventIds(recurringEventIds);
            
            // Batch fetch all excluded dates for all recurring events in one query
            var allExcludedDatesData = exceptionRepository.findExcludedOccurrenceDatesForEvents(recurringEventIds);
            
            // Group exceptions by eventId for efficient lookup
            var exceptionsByEventId = allExceptions.stream()
                    .collect(Collectors.groupingBy(exception -> exception.getEvent().getId()));
            
            // Group excluded dates by eventId for efficient lookup
            var excludedDatesByEventId = new java.util.HashMap<UUID, java.util.Set<LocalDate>>();
            for (var row : allExcludedDatesData) {
                var eventId = (UUID) row[0];
                var occurrenceDate = (LocalDate) row[1];
                excludedDatesByEventId.computeIfAbsent(eventId, k -> new java.util.HashSet<>()).add(occurrenceDate);
            }
            
            // Generate recurring instances only for events that could match the range
            for (var recurringEvent : allRecurringEvents) {
                // Validate date range based on recurring type
                validateDateRangeForRecurringType(recurringEvent.recurringType(), startDate, endDate);
                
                // Get excluded occurrence dates for this event from pre-fetched map
                var excludedDatesSet = excludedDatesByEventId.getOrDefault(recurringEvent.id(), java.util.Collections.emptySet());
                var excludedDates = new java.util.ArrayList<>(excludedDatesSet);
                
                var instances = generateRecurringInstances(recurringEvent, startDate, endDate, excludedDates);
                result.addAll(instances);
                
                // Get modified events from exceptions (events created when editing a single occurrence)
                var exceptions = exceptionsByEventId.getOrDefault(recurringEvent.id(), java.util.List.of());
                for (var exception : exceptions) {
                    if (exception.getModifiedEvent() != null) {
                        var modifiedEventEntity = exception.getModifiedEvent();
                        var modifiedEvent = toDomain(modifiedEventEntity);
                        // Only include if modified event is within the date range
                        if (!modifiedEvent.startDateTime().isBefore(startDate) && !modifiedEvent.startDateTime().isAfter(endDate)) {
                            result.add(modifiedEvent);
                        }
                    }
                }
                
                // Include base event if it's within the date range (for editing purposes)
                // But only if no instances were generated for the base event's start date
                // (to avoid duplicates)
                var baseEventStart = recurringEvent.startDateTime();
                if (!baseEventStart.isBefore(startDate) && !baseEventStart.isAfter(endDate)) {
                    // Check if base event's start date is excluded
                    if (!excludedDates.contains(baseEventStart.toLocalDate())) {
                        // Check if there's already an instance for the base event's start date
                        boolean hasInstanceForBaseDate = instances.stream()
                                .anyMatch(instance -> instance.startDateTime().toLocalDate().equals(baseEventStart.toLocalDate()));
                        if (!hasInstanceForBaseDate) {
                            // Add base event if no instance exists for its start date
                            result.add(recurringEvent);
                        }
                    }
                }
            }
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
    
    /**
     * Validates that the date range is acceptable for the given recurring type.
     * Throws IllegalArgumentException if the range exceeds the maximum allowed.
     * 
     * Rules:
     * - DAILY: max 1 year
     * - WEEKLY: max 2 years
     * - MONTHLY: max 3 years
     * - YEARLY: max 10 years
     */
    private void validateDateRangeForRecurringType(
            CalendarEvent.RecurringType recurringType,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        if (recurringType == null) {
            return; // Not a recurring event, no validation needed
        }
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(rangeStart, rangeEnd);
        long maxDays;
        
        switch (recurringType) {
            case DAILY -> maxDays = 365; // 1 year
            case WEEKLY -> maxDays = 730; // 2 years
            case MONTHLY -> maxDays = 1095; // 3 years
            case YEARLY -> maxDays = 3650; // 10 years
            default -> maxDays = 365; // Default to 1 year for safety
        }
        
        if (daysBetween > maxDays) {
            throw new IllegalArgumentException(
                String.format("Date range exceeds maximum allowed for %s recurring events. " +
                    "Maximum is %d days (approximately %s), but requested range is %d days.",
                    recurringType.name(),
                    maxDays,
                    formatDaysAsYears(maxDays),
                    daysBetween
                )
            );
        }
    }
    
    /**
     * Helper method to format days as a human-readable string.
     */
    private String formatDaysAsYears(long days) {
        if (days >= 3650) {
            return String.format("%.1f years", days / 365.0);
        } else if (days >= 365) {
            return String.format("%.1f year%s", days / 365.0, days >= 730 ? "s" : "");
        } else {
            return String.format("%d days", days);
        }
    }
    
    /**
     * Calculates a default recurring end date based on the recurring type.
     * This ensures events don't recur indefinitely, which could cause memory issues.
     * 
     * Rules:
     * - DAILY: 1 year from start date
     * - WEEKLY: 2 years from start date
     * - MONTHLY: 3 years from start date
     * - YEARLY: 10 years from start date
     */
    private LocalDate calculateDefaultRecurringEndDate(
            CalendarEvent.RecurringType recurringType,
            LocalDate startDate
    ) {
        return switch (recurringType) {
            case DAILY -> startDate.plusYears(1);
            case WEEKLY -> startDate.plusYears(2);
            case MONTHLY -> startDate.plusYears(3);
            case YEARLY -> startDate.plusYears(10);
        };
    }
    
    private List<CalendarEvent> generateRecurringInstances(
            CalendarEvent baseEvent,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            List<LocalDate> excludedDates
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
            // and not excluded
            if (!currentStart.isBefore(rangeStart) && !currentStart.isAfter(rangeEnd)) {
                // Skip if this occurrence is excluded
                if (excludedDates.contains(currentStart.toLocalDate())) {
                    iteration++;
                    currentStart = getNextOccurrence(currentStart, recurringType, interval);
                    if (currentEnd != null) {
                        currentEnd = currentStart.plus(duration);
                    }
                    continue;
                }
                
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
            
            // If no end date or end count is specified, set a default end date based on recurring type
            // This prevents events from recurring indefinitely and causing memory issues
            if (recurringEndDate == null && recurringEndCount == null) {
                recurringEndDate = calculateDefaultRecurringEndDate(recurringType, startDateTime.toLocalDate());
            }
            
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
            
            // If no end date or end count is specified, set a default end date based on recurring type
            // This prevents events from recurring indefinitely and causing memory issues
            LocalDate finalRecurringEndDate = recurringEndDate;
            if (finalRecurringEndDate == null && recurringEndCount == null) {
                finalRecurringEndDate = calculateDefaultRecurringEndDate(recurringType, startDateTime.toLocalDate());
            }
            
            entity.setRecurringEndDate(finalRecurringEndDate);
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

    @CacheEvict(value = "events", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void deleteEventWithScope(UUID eventId, LocalDate occurrenceDate, CalendarEvent.OccurrenceScope scope) {
        // Validate inputs
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (occurrenceDate == null) {
            throw new IllegalArgumentException("occurrenceDate cannot be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope cannot be null");
        }
        
        var entity = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));
        
        var baseEvent = toDomain(entity);
        
        if (!baseEvent.isRecurring()) {
            // Not a recurring event, just delete it
            eventRepository.deleteById(eventId);
            return;
        }
        
        switch (scope) {
            case THIS -> {
                // Check if exception already exists (e.g., from a previous edit)
                var existingException = exceptionRepository.findByEventIdAndOccurrenceDate(eventId, occurrenceDate);
                
                if (existingException.isPresent()) {
                    var exception = existingException.get();
                    // If there's a modified event, delete it first
                    if (exception.getModifiedEvent() != null) {
                        eventRepository.deleteById(exception.getModifiedEvent().getId());
                    }
                    // Delete the exception (it will be recreated below if needed, but for delete we just exclude)
                    exceptionRepository.delete(exception);
                }
                
                // Create exception for this occurrence to exclude it
                var exception = new CalendarEventExceptionEntity();
                exception.setId(UUID.randomUUID());
                exception.setEvent(entity);
                exception.setOccurrenceDate(occurrenceDate);
                exception.setCreatedAt(OffsetDateTime.now());
                // No modifiedEvent for delete operations
                exceptionRepository.save(exception);
            }
            case THIS_AND_FOLLOWING -> {
                // Set recurring end date to day before this occurrence
                var endDate = occurrenceDate.minusDays(1);
                entity.setRecurringEndDate(endDate);
                entity.setUpdatedAt(OffsetDateTime.now());
                eventRepository.save(entity);
            }
            case ALL -> {
                // Delete entire event (cascade will handle exceptions)
                eventRepository.deleteById(eventId);
            }
        }
    }

    @CacheEvict(value = "events", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public CalendarEvent updateEventWithScope(
            UUID eventId,
            LocalDate occurrenceDate,
            CalendarEvent.OccurrenceScope scope,
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
        // Validate inputs
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (occurrenceDate == null) {
            throw new IllegalArgumentException("occurrenceDate cannot be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope cannot be null");
        }
        
        var entity = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + eventId));
        
        var baseEvent = toDomain(entity);
        
        // IMPORTANT: For scope THIS, we should NOT modify the base event's recurring settings
        // The base event should remain unchanged, we only create an exception and a new modified event
        
        if (!baseEvent.isRecurring()) {
            // Not a recurring event, just update it normally
            return updateEvent(eventId, categoryId, title, description, startDateTime, endDateTime, 
                    isAllDay, location, participantIds, recurringType, recurringInterval, 
                    recurringEndDate, recurringEndCount, isTask, xpPoints, isRequired);
        }
        
        // Validate that recurringType is provided for scopes that require it
        if ((scope == CalendarEvent.OccurrenceScope.THIS_AND_FOLLOWING || scope == CalendarEvent.OccurrenceScope.ALL) 
                && recurringType == null) {
            throw new IllegalArgumentException("recurringType is required for scope " + scope);
        }
        
        switch (scope) {
            case THIS -> {
                // Check if exception already exists for this occurrence
                // Handle potential race conditions: if two requests try to create exception simultaneously,
                // the UNIQUE constraint will prevent duplicates, but we should handle it gracefully
                var existingException = exceptionRepository.findByEventIdAndOccurrenceDate(eventId, occurrenceDate);
                
                CalendarEventExceptionEntity exception;
                UUID oldModifiedEventId = null;
                
                if (existingException.isPresent()) {
                    // Update existing exception
                    exception = existingException.get();
                    // If there's an existing modified event, we need to delete it to avoid orphaned events
                    if (exception.getModifiedEvent() != null) {
                        oldModifiedEventId = exception.getModifiedEvent().getId();
                    }
                } else {
                    // Create new exception for original occurrence
                    exception = new CalendarEventExceptionEntity();
                    exception.setId(UUID.randomUUID());
                    exception.setEvent(entity);
                    exception.setOccurrenceDate(occurrenceDate);
                    exception.setCreatedAt(OffsetDateTime.now());
                    
                    // Try to save, but handle race condition where another thread creates it between check and save
                    try {
                        exceptionRepository.save(exception);
                    } catch (DataIntegrityViolationException e) {
                        // Another thread created the exception, fetch it and use that instead
                        existingException = exceptionRepository.findByEventIdAndOccurrenceDate(eventId, occurrenceDate);
                        if (existingException.isEmpty()) {
                            throw new IllegalStateException("Failed to create exception and could not find existing one", e);
                        }
                        exception = existingException.get();
                        if (exception.getModifiedEvent() != null) {
                            oldModifiedEventId = exception.getModifiedEvent().getId();
                        }
                    }
                }
                
                // Create new event for modified occurrence
                var newEvent = createEvent(
                        baseEvent.familyId(),
                        categoryId,
                        title,
                        description,
                        startDateTime,
                        endDateTime,
                        isAllDay,
                        location,
                        baseEvent.createdById(),
                        participantIds,
                        null, // Not recurring
                        null,
                        null,
                        null,
                        isTask != null ? isTask : baseEvent.isTask(),
                        xpPoints,
                        isRequired != null ? isRequired : baseEvent.isRequired()
                );
                
                var newEventEntity = eventRepository.findById(newEvent.id()).orElseThrow();
                exception.setModifiedEvent(newEventEntity);
                // Save or update exception (JPA will handle whether it's insert or update)
                exceptionRepository.save(exception);
                
                // Delete old modified event if it existed (after saving new exception to ensure transaction consistency)
                if (oldModifiedEventId != null) {
                    eventRepository.deleteById(oldModifiedEventId);
                }
                
                return newEvent;
            }
            case THIS_AND_FOLLOWING -> {
                // Set recurring end date to day before this occurrence for original event
                var endDate = occurrenceDate.minusDays(1);
                entity.setRecurringEndDate(endDate);
                entity.setUpdatedAt(OffsetDateTime.now());
                eventRepository.save(entity);
                
                // Create new recurring event starting from this occurrence
                return createEvent(
                        baseEvent.familyId(),
                        categoryId,
                        title,
                        description,
                        startDateTime,
                        endDateTime,
                        isAllDay,
                        location,
                        baseEvent.createdById(),
                        participantIds,
                        recurringType,
                        recurringInterval,
                        recurringEndDate,
                        recurringEndCount,
                        isTask != null ? isTask : baseEvent.isTask(),
                        xpPoints,
                        isRequired != null ? isRequired : baseEvent.isRequired()
                );
            }
            case ALL -> {
                // Update entire recurring event
                return updateEvent(eventId, categoryId, title, description, startDateTime, endDateTime,
                        isAllDay, location, participantIds, recurringType, recurringInterval,
                        recurringEndDate, recurringEndCount, isTask, xpPoints, isRequired);
            }
        }
        
        throw new IllegalArgumentException("Invalid scope: " + scope);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "#familyId != null ? #familyId.toString() : 'all'")
    public List<CalendarEventCategory> getAllCategories(UUID familyId) {
        return categoryRepository.findByFamilyIdOrderByNameAsc(familyId).stream()
                .map(this::toDomainCategory)
                .toList();
    }

    /**
     * Creates a new calendar category.
     * 
     * Cache behavior:
     * - Evicts "categories" cache for this family
     * 
     * @param familyId The family ID
     * @param name The category name
     * @param color The category color
     * @return The created category
     * @throws IllegalArgumentException if a category with this name already exists for the family
     */
    @CacheEvict(value = "categories", key = "#familyId != null ? #familyId.toString() : 'all'")
    public CalendarEventCategory createCategory(UUID familyId, String name, String color) {
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        
        // Check if category with this name already exists for this family
        if (familyId != null) {
            var existingCategory = categoryRepository.findByFamilyIdAndName(familyId, name.trim());
            if (existingCategory.isPresent()) {
                throw new IllegalArgumentException("En kategori med namnet '" + name.trim() + "' finns redan");
            }
        }
        
        var now = OffsetDateTime.now();
        var entity = new CalendarEventCategoryEntity();
        entity.setId(UUID.randomUUID());
        
        if (familyId != null) {
            familyRepository.findById(familyId).ifPresent(entity::setFamily);
        }
        
        entity.setName(name.trim());
        entity.setColor(color != null ? color : "#b8e6b8");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        try {
            var saved = categoryRepository.save(entity);
            var result = toDomainCategory(saved);
            
            // Evict categories cache for this specific family (targeted eviction)
            cacheService.evictCategories(familyId);
            
            return result;
        } catch (DataIntegrityViolationException e) {
            // Handle unique constraint violation (category name already exists)
            if (e.getMessage() != null && e.getMessage().contains("unique_category_name_per_family")) {
                throw new IllegalArgumentException("En kategori med namnet '" + name.trim() + "' finns redan");
            }
            throw e;
        }
    }

    /**
     * Updates a calendar category.
     * 
     * Cache behavior:
     * - Evicts "categories" cache for this category's family
     * 
     * @param categoryId The category to update
     * @param name The new name
     * @param color The new color
     * @return The updated category
     * @throws IllegalArgumentException if a category with this name already exists for the family (excluding the current category)
     */
    @CacheEvict(value = "categories", allEntries = true)
    public CalendarEventCategory updateCategory(UUID categoryId, String name, String color) {
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        
        var entity = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        // Check if another category with this name already exists for this family
        if (familyId != null) {
            var existingCategory = categoryRepository.findByFamilyIdAndName(familyId, name.trim());
            if (existingCategory.isPresent() && !existingCategory.get().getId().equals(categoryId)) {
                throw new IllegalArgumentException("En kategori med namnet '" + name.trim() + "' finns redan");
            }
        }
        
        entity.setName(name.trim());
        if (color != null) {
            entity.setColor(color);
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        
        try {
            var saved = categoryRepository.save(entity);
            var result = toDomainCategory(saved);
            
            // Cache is evicted by @CacheEvict annotation
            
            return result;
        } catch (DataIntegrityViolationException e) {
            // Handle unique constraint violation (category name already exists)
            if (e.getMessage() != null && e.getMessage().contains("unique_category_name_per_family")) {
                throw new IllegalArgumentException("En kategori med namnet '" + name.trim() + "' finns redan");
            }
            throw e;
        }
    }

    /**
     * Deletes a calendar category.
     * 
     * Cache behavior:
     * - Evicts "categories" cache for this category's family
     * 
     * @param categoryId The category to delete
     */
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(UUID categoryId) {
        // Check if category exists before deletion
        if (!categoryRepository.existsById(categoryId)) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }
        
        categoryRepository.deleteById(categoryId);
        
        // Cache is evicted by @CacheEvict annotation
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
        
        // Use memberService to get member (uses cache and proper domain conversion)
        // This ensures we get a properly loaded entity with all relations
        var memberDomain = memberService.getMemberById(memberId);
        
        // Get member entity for validation (need to check family relation)
        // Use memberService to ensure cache is used and entity is properly loaded
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        
        // Ensure family is loaded (prevent lazy loading issues with cached entities)
        if (member.getFamily() != null) {
            member.getFamily().getId(); // Trigger lazy load within transaction
        }
        
        // Validate: Member must be in the same family as the event
        // Use memberDomain.familyId() which is already loaded (from cache or DB)
        if (eventEntity.getFamily() == null || memberDomain.familyId() == null ||
            !eventEntity.getFamily().getId().equals(memberDomain.familyId())) {
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
        
        // Add food to collection when task is completed
        Integer xpPoints = eventEntity.getXpPoints();
        if (xpPoints != null && xpPoints > 0) {
            try {
                foodService.addFoodFromTask(memberId, eventId, xpPoints);
            } catch (Exception e) {
                // Log error but don't fail the task completion
                // Note: Using System.err for now as this service doesn't have a logger yet
                // TODO: Add SLF4J logger to CalendarService for proper logging
                System.err.println("Failed to add food for task completion: eventId=" + eventId + 
                    ", memberId=" + memberId + ", xpPoints=" + xpPoints + ", error=" + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // NOTE: XP is no longer awarded here. XP is awarded when the child feeds their pet.
        // This allows the child to collect food from tasks and then feed the pet when ready.
        
        // Ensure event and member are loaded before converting to domain
        // This prevents lazy loading issues
        saved.getEvent().getId(); // Trigger lazy load
        saved.getMember().getId(); // Trigger lazy load
        
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
            
            // Remove food from collection when task is uncompleted
            // Only removes unfed food - if food has been fed, it cannot be removed
            Integer xpPoints = eventEntity.getXpPoints();
            if (xpPoints != null && xpPoints > 0) {
                try {
                    foodService.removeFoodFromTask(memberId, eventId, xpPoints);
                } catch (IllegalArgumentException e) {
                    // Not enough unfed food - throw error to prevent uncompletion
                    // The error message from removeFoodFromTask already contains detailed information
                    throw e;
                } catch (Exception e) {
                    // Log other errors but don't fail the uncompletion
                    // Note: Using System.err for now as this service doesn't have a logger yet
                    // TODO: Add SLF4J logger to CalendarService for proper logging
                    System.err.println("Failed to remove food for task uncompletion: eventId=" + eventId + 
                        ", memberId=" + memberId + ", xpPoints=" + xpPoints + ", error=" + e.getMessage());
                    e.printStackTrace();
                    // Re-throw as IllegalArgumentException to provide user-friendly message
                    throw new IllegalArgumentException(
                            "Kan inte avmarkera syssla: Ett fel uppstod när mat skulle tas bort. " +
                            "Kontrollera att du har tillräckligt med omatad mat."
                    );
                }
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

