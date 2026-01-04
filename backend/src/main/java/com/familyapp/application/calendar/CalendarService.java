package com.familyapp.application.calendar;

import com.familyapp.domain.calendar.CalendarEvent;
import com.familyapp.domain.calendar.CalendarEventCategory;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryEntity;
import com.familyapp.infrastructure.calendar.CalendarEventCategoryJpaRepository;
import com.familyapp.infrastructure.calendar.CalendarEventEntity;
import com.familyapp.infrastructure.calendar.CalendarEventJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CalendarService(
            CalendarEventJpaRepository eventRepository,
            CalendarEventCategoryJpaRepository categoryRepository,
            FamilyMemberJpaRepository memberRepository,
            FamilyJpaRepository familyRepository
    ) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> getEventsForDateRange(UUID familyId, LocalDateTime startDate, LocalDateTime endDate) {
        var baseEvents = eventRepository.findByFamilyIdAndDateRange(familyId, startDate, endDate).stream()
                .map(this::toDomain)
                .toList();
        
        // Also get recurring events that might generate instances in this range
        var allRecurringEvents = eventRepository.findByFamilyIdOrderByStartDateTimeAsc(familyId).stream()
                .map(this::toDomain)
                .filter(CalendarEvent::isRecurring)
                .toList();
        
        var result = new java.util.ArrayList<CalendarEvent>(baseEvents);
        
        // Generate recurring instances
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
        var baseEvents = eventRepository.findByFamilyIdOrderByStartDateTimeAsc(familyId).stream()
                .map(this::toDomain)
                .toList();
        
        var result = new java.util.ArrayList<CalendarEvent>();
        var now = LocalDateTime.now();
        var futureLimit = now.plusYears(2); // Generate instances up to 2 years in the future
        
        for (var event : baseEvents) {
            if (event.isRecurring()) {
                // Generate recurring instances from now to future
                // Use the event's start date as the earliest possible date (in case it's in the future)
                var rangeStart = event.startDateTime().isAfter(now) ? event.startDateTime() : now;
                var instances = generateRecurringInstances(event, rangeStart, futureLimit);
                result.addAll(instances);
            } else {
                // Non-recurring event, add as-is (even if in the past, for now)
                result.add(event);
            }
        }
        
        return result.stream()
                .sorted((a, b) -> a.startDateTime().compareTo(b.startDateTime()))
                .toList();
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
            Integer recurringEndCount
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
            Integer recurringEndCount
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
}

