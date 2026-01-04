package com.familyapp.api.calendar;

import com.familyapp.application.calendar.CalendarService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.calendar.CalendarEvent;
import com.familyapp.domain.calendar.CalendarEventCategory;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final CalendarService service;
    private final FamilyMemberService memberService;

    public CalendarController(CalendarService service, FamilyMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
    }

    @GetMapping("/events")
    public List<CalendarEventResponse> getEvents(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, return empty list
                return List.of();
            }
        }
        
        if (familyId == null) {
            return List.of();
        }
        
        List<CalendarEvent> events;
        if (startDate != null && endDate != null) {
            events = service.getEventsForDateRange(familyId, startDate, endDate);
        } else {
            events = service.getAllEvents(familyId);
        }
        
        return events.stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse createEvent(
            @RequestBody CreateCalendarEventRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        UUID createdById = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
                createdById = member.id();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token");
            }
        }
        
        if (familyId == null) {
            throw new IllegalArgumentException("Family ID is required to create an event.");
        }
        
        CalendarEvent.RecurringType recurringType = null;
        if (request.recurringType() != null && !request.recurringType().isEmpty()) {
            try {
                recurringType = CalendarEvent.RecurringType.valueOf(request.recurringType());
            } catch (IllegalArgumentException e) {
                // Invalid recurring type, leave as null
            }
        }
        
        var event = service.createEvent(
                familyId,
                request.categoryId(),
                request.title(),
                request.description(),
                request.startDateTime(),
                request.endDateTime(),
                request.isAllDay() != null ? request.isAllDay() : false,
                request.location(),
                createdById,
                request.participantIds() != null ? request.participantIds() : Set.of(),
                recurringType,
                request.recurringInterval(),
                request.recurringEndDate(),
                request.recurringEndCount()
        );
        
        return toResponse(event);
    }

    @PatchMapping("/events/{eventId}")
    public CalendarEventResponse updateEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody UpdateCalendarEventRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Validate token (familyId not needed for update, but we validate token)
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                memberService.getMemberByDeviceToken(deviceToken);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token");
            }
        }
        
        CalendarEvent.RecurringType recurringType = null;
        if (request.recurringType() != null && !request.recurringType().isEmpty()) {
            try {
                recurringType = CalendarEvent.RecurringType.valueOf(request.recurringType());
            } catch (IllegalArgumentException e) {
                // Invalid recurring type, leave as null
            }
        }
        
        var event = service.updateEvent(
                eventId,
                request.categoryId(),
                request.title(),
                request.description(),
                request.startDateTime(),
                request.endDateTime(),
                request.isAllDay() != null ? request.isAllDay() : false,
                request.location(),
                request.participantIds() != null ? request.participantIds() : Set.of(),
                recurringType,
                request.recurringInterval(),
                request.recurringEndDate(),
                request.recurringEndCount()
        );
        
        return toResponse(event);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable("eventId") UUID eventId) {
        service.deleteEvent(eventId);
    }

    @GetMapping("/categories")
    public List<CalendarEventCategoryResponse> getCategories(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        
        if (familyId == null) {
            return List.of();
        }
        
        return service.getAllCategories(familyId).stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventCategoryResponse createCategory(
            @RequestBody CreateCalendarEventCategoryRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid device token");
            }
        }
        
        if (familyId == null) {
            throw new IllegalArgumentException("Family ID is required to create a category.");
        }
        
        var category = service.createCategory(familyId, request.name(), request.color());
        return toCategoryResponse(category);
    }

    @PatchMapping("/categories/{categoryId}")
    public CalendarEventCategoryResponse updateCategory(
            @PathVariable("categoryId") UUID categoryId,
            @RequestBody UpdateCalendarEventCategoryRequest request
    ) {
        var category = service.updateCategory(categoryId, request.name(), request.color());
        return toCategoryResponse(category);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable("categoryId") UUID categoryId) {
        service.deleteCategory(categoryId);
    }

    private CalendarEventResponse toResponse(CalendarEvent event) {
        // Format LocalDateTime as "yyyy-MM-ddTHH:mm" (no timezone, no seconds)
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        return new CalendarEventResponse(
                event.id(),
                event.familyId(),
                event.categoryId(),
                event.title(),
                event.description(),
                event.startDateTime() != null ? event.startDateTime().format(formatter) : null,
                event.endDateTime() != null ? event.endDateTime().format(formatter) : null,
                event.isAllDay(),
                event.location(),
                event.createdById(),
                event.recurringType() != null ? event.recurringType().name() : null,
                event.recurringInterval(),
                event.recurringEndDate() != null ? event.recurringEndDate().toString() : null,
                event.recurringEndCount(),
                event.createdAt().toString(),
                event.updatedAt().toString(),
                event.participantIds()
        );
    }

    private CalendarEventCategoryResponse toCategoryResponse(CalendarEventCategory category) {
        return new CalendarEventCategoryResponse(
                category.id(),
                category.familyId(),
                category.name(),
                category.color(),
                category.createdAt().toString(),
                category.updatedAt().toString()
        );
    }

    public record CreateCalendarEventRequest(
            UUID categoryId,
            @NotBlank(message = "Title is required")
            String title,
            String description,
            @NotBlank(message = "Start date/time is required")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime startDateTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime endDateTime,
            Boolean isAllDay,
            String location,
            Set<UUID> participantIds,
            String recurringType,
            Integer recurringInterval,
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate recurringEndDate,
            Integer recurringEndCount
    ) {
    }

    public record UpdateCalendarEventRequest(
            UUID categoryId,
            @NotBlank(message = "Title is required")
            String title,
            String description,
            @NotBlank(message = "Start date/time is required")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime startDateTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime endDateTime,
            Boolean isAllDay,
            String location,
            Set<UUID> participantIds,
            String recurringType,
            Integer recurringInterval,
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate recurringEndDate,
            Integer recurringEndCount
    ) {
    }

    public record CalendarEventResponse(
            UUID id,
            UUID familyId,
            UUID categoryId,
            String title,
            String description,
            String startDateTime,
            String endDateTime,
            boolean isAllDay,
            String location,
            UUID createdById,
            String recurringType,
            Integer recurringInterval,
            String recurringEndDate,
            Integer recurringEndCount,
            String createdAt,
            String updatedAt,
            Set<UUID> participantIds
    ) {
    }

    public record CreateCalendarEventCategoryRequest(
            @NotBlank(message = "Name is required")
            String name,
            String color
    ) {
    }

    public record UpdateCalendarEventCategoryRequest(
            @NotBlank(message = "Name is required")
            String name,
            String color
    ) {
    }

    public record CalendarEventCategoryResponse(
            UUID id,
            UUID familyId,
            String name,
            String color,
            String createdAt,
            String updatedAt
    ) {
    }
}

