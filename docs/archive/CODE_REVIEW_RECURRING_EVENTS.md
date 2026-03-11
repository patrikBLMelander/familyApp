# Code Review: Recurring Events Implementation

**Reviewer:** Senior Developer  
**Date:** 2025-01-27  
**Scope:** Recurring events functionality with exception handling

---

## Executive Summary

The implementation successfully adds support for recurring events with the ability to edit or delete individual occurrences. The architecture follows a solid pattern using exceptions to track modifications. However, there are several areas that need attention regarding performance, error handling, code maintainability, and edge cases.

**Overall Assessment:** ⚠️ **Good foundation, but needs improvements before production**

---

## Strengths ✅

### 1. **Clean Architecture**
- Good separation between domain, application, and infrastructure layers
- Exception-based approach for tracking modifications is elegant and follows industry best practices (similar to Google Calendar)
- Database schema is well-designed with proper foreign keys and indexes

### 2. **Database Design**
- `calendar_event_exception` table has proper constraints (UNIQUE key, foreign keys with CASCADE)
- Good indexing strategy for query performance
- Clear documentation in migration file

### 3. **Frontend UX**
- User-friendly dialog for selecting scope
- Responsive design considerations
- Clear visual feedback

---

## Critical Issues 🔴

### 1. **N+1 Query Problem in `getEventsForDateRange`**

**Location:** `CalendarService.java:115-132`

```java
for (var recurringEvent : allRecurringEvents) {
    var excludedDates = exceptionRepository.findExcludedOccurrenceDates(recurringEvent.id());
    var instances = generateRecurringInstances(...);
    
    var exceptions = exceptionRepository.findByEventId(recurringEvent.id());
    for (var exception : exceptions) {
        // Process each exception
    }
}
```

**Problem:** For each recurring event, we make 2 separate database queries. With 100 recurring events, this becomes 200 queries.

**Impact:** Severe performance degradation as the number of recurring events grows.

**Recommendation:**
```java
// Batch fetch all exceptions at once
var allExceptionDates = exceptionRepository.findExcludedOccurrenceDatesForEvents(
    allRecurringEvents.stream().map(CalendarEvent::id).toList()
);
var allExceptions = exceptionRepository.findByEventIds(
    allRecurringEvents.stream().map(CalendarEvent::id).toList()
);
// Then group by eventId in memory
```

### 2. **Missing Transaction Boundaries**

**Location:** `CalendarService.updateEventWithScope` (THIS case)

```java
case THIS -> {
    // ... create exception ...
    var newEvent = createEvent(...);
    exception.setModifiedEvent(eventRepository.findById(newEvent.id()).orElseThrow());
    exceptionRepository.save(exception);
    return newEvent;
}
```

**Problem:** If `exceptionRepository.save(exception)` fails, we've created an orphaned event. No rollback mechanism.

**Impact:** Data inconsistency - orphaned events in database.

**Recommendation:** Ensure entire method is `@Transactional` (it appears to be, but verify). Consider using `@Transactional(rollbackFor = Exception.class)`.

### 3. **Race Condition in Exception Creation**

**Location:** `CalendarService.updateEventWithScope:550-564`

```java
var existingException = exceptionRepository.findByEventIdAndOccurrenceDate(eventId, occurrenceDate);

CalendarEventExceptionEntity exception;
if (existingException.isPresent()) {
    exception = existingException.get();
} else {
    // Create new exception
    exception = new CalendarEventExceptionEntity();
    // ...
}
```

**Problem:** Two concurrent requests editing the same occurrence could both pass the `existingException.isPresent()` check and create duplicate exceptions.

**Impact:** Potential duplicate exceptions or data corruption.

**Recommendation:** Use database-level locking or optimistic locking:
```java
@Version
private Long version; // Add to CalendarEventExceptionEntity
```

Or use `INSERT ... ON DUPLICATE KEY UPDATE` at database level.

### 4. **Memory Exhaustion Risk in `generateRecurringInstances`**

**Location:** `CalendarService.generateRecurringInstances`

```java
int maxIterations = 1000; // Safety limit
```

**Problem:** 
- For a daily recurring event with no end date, fetching 1 year = 365 instances
- Fetching 10 years = 3,650 instances
- All loaded into memory at once

**Impact:** OutOfMemoryError for large date ranges or long-running recurring events.

**Recommendation:**
- Add pagination or streaming
- Consider database-level generation instead of in-memory
- Add configurable limits and warn users

### 5. **Incomplete Exception Update Logic**

**Location:** `CalendarService.updateEventWithScope:551-564`

```java
if (existingException.isPresent()) {
    exception = existingException.get();
} else {
    // Create new exception
}
// ... create new event ...
exception.setModifiedEvent(...);
exceptionRepository.save(exception);
```

**Problem:** When updating an existing exception, we don't handle the case where a modified event already exists. Should we:
- Delete the old modified event?
- Update it?
- Create a new one and orphan the old?

**Impact:** Memory leaks (orphaned events) and data inconsistency.

**Recommendation:** Add logic to handle existing modified events:
```java
if (existingException.isPresent()) {
    exception = existingException.get();
    // If there's an existing modified event, delete it first
    if (exception.getModifiedEvent() != null) {
        eventRepository.deleteById(exception.getModifiedEvent().getId());
    }
}
```

---

## Major Issues 🟠

### 6. **Type Safety Issues in Frontend**

**Location:** `EventForm.tsx:112-113`

```typescript
const recurringScope = (event as any)?.__recurringScope as "THIS" | "THIS_AND_FOLLOWING" | "ALL" | undefined;
const occurrenceDate = (event as any)?.__occurrenceDate as string | undefined;
```

**Problem:** Using `any` type casting defeats TypeScript's purpose. Magic properties `__recurringScope` and `__occurrenceDate` are not part of the type system.

**Impact:** Runtime errors, harder to maintain, no IDE support.

**Recommendation:**
```typescript
type CalendarEventWithScope = CalendarEventResponse & {
  __recurringScope?: "THIS" | "THIS_AND_FOLLOWING" | "ALL";
  __occurrenceDate?: string;
};
```

### 7. **Inefficient Event Lookup in Frontend**

**Location:** `RollingView.tsx:126`

```typescript
const eventsWithSameId = events.filter(e => e.id === event.id);
```

**Problem:** Linear search through all events for every check. Called in `useCallback` that depends on `events`, so recalculated frequently.

**Impact:** Performance degradation with many events.

**Recommendation:** Use a `Map<string, CalendarEventResponse[]>` or `useMemo` to index events by ID:
```typescript
const eventsById = useMemo(() => {
  const map = new Map<string, CalendarEventResponse[]>();
  events.forEach(e => {
    const existing = map.get(e.id) || [];
    map.set(e.id, [...existing, e]);
  });
  return map;
}, [events]);
```

### 8. **Missing Validation**

**Location:** `CalendarService.updateEventWithScope`

**Problem:** No validation that:
- `occurrenceDate` is a valid date for the recurring event
- `occurrenceDate` is not in the past (if business rule requires)
- `recurringType` is provided when scope is `THIS_AND_FOLLOWING` or `ALL`

**Impact:** Invalid data in database, confusing error messages.

**Recommendation:** Add validation:
```java
if (scope == THIS_AND_FOLLOWING || scope == ALL) {
    if (recurringType == null) {
        throw new IllegalArgumentException("recurringType is required for scope " + scope);
    }
}
```

### 9. **Inconsistent Error Handling**

**Location:** Multiple locations

**Problem:** Some methods throw `IllegalArgumentException`, others might throw different exceptions. No consistent error handling strategy.

**Impact:** Frontend can't handle errors consistently.

**Recommendation:** Create custom exception hierarchy:
```java
public class CalendarEventException extends RuntimeException { }
public class EventNotFoundException extends CalendarEventException { }
public class InvalidOccurrenceDateException extends CalendarEventException { }
```

### 10. **Missing Logging**

**Location:** Throughout `CalendarService`

**Problem:** No logging for:
- When exceptions are created/updated
- When recurring instances are generated
- Performance metrics

**Impact:** Difficult to debug production issues.

**Recommendation:** Add structured logging:
```java
log.info("Creating exception for event {} on occurrence {}", eventId, occurrenceDate);
log.debug("Generated {} instances for recurring event {}", instances.size(), eventId);
```

---

## Minor Issues 🟡

### 11. **Code Duplication**

**Location:** `RecurringEventDialog.tsx` - Three nearly identical button components

**Recommendation:** Extract to a reusable component:
```typescript
function ScopeButton({ scope, title, description, onClick }: ScopeButtonProps) { ... }
```

### 12. **Magic Numbers**

**Location:** `CalendarService.generateRecurringInstances:191`

```java
int maxIterations = 1000; // Safety limit
```

**Recommendation:** Extract to configuration:
```java
@Value("${calendar.recurring.max-iterations:1000}")
private int maxRecurringIterations;
```

### 13. **Inconsistent Date Handling**

**Location:** Frontend - mixing string dates and Date objects

**Problem:** `occurrenceDate` is string, but converted to Date for display. Risk of timezone issues.

**Recommendation:** Use a date utility library (date-fns, dayjs) or create consistent helpers.

### 14. **Missing Javadoc**

**Location:** `CalendarService.updateEventWithScope`, `deleteEventWithScope`

**Recommendation:** Add comprehensive JavaDoc explaining:
- What each scope does
- Side effects
- When to use each method

### 15. **Hardcoded Strings**

**Location:** `RecurringEventDialog.tsx` - All text is hardcoded

**Recommendation:** Extract to i18n strings for future internationalization.

---

## Architecture & Design Concerns

### 16. **Tight Coupling**

**Problem:** `CalendarService` directly depends on JPA repositories. Makes testing harder.

**Recommendation:** Consider introducing a repository interface in the domain layer.

### 17. **Missing Domain Events**

**Problem:** No event publishing when exceptions are created. Other parts of the system can't react.

**Recommendation:** Consider Spring Events or domain events pattern:
```java
applicationEventPublisher.publishEvent(new RecurringEventExceptionCreatedEvent(...));
```

### 18. **Cache Invalidation Strategy**

**Location:** `@CacheEvict(value = "events", allEntries = true)`

**Problem:** Invalidates entire cache on any change. With many events, this is inefficient.

**Recommendation:** Consider more granular cache keys or cache per family:
```java
@CacheEvict(value = "events", key = "#familyId")
```

---

## Testing Concerns

### 19. **No Unit Tests Visible**

**Problem:** No test files found for the new functionality.

**Recommendation:** Add comprehensive tests:
- Unit tests for `generateRecurringInstances`
- Unit tests for `updateEventWithScope` (all scopes)
- Integration tests for exception creation
- Edge cases (overlapping exceptions, concurrent updates)

### 20. **Missing Edge Case Handling**

**Scenarios to test:**
- Editing an occurrence that already has an exception
- Deleting an occurrence that was previously edited
- Editing with `THIS_AND_FOLLOWING` when original event has no end date
- Very large date ranges
- Events with thousands of occurrences

---

## Security Concerns

### 21. **No Authorization Checks**

**Location:** `CalendarController.updateEvent`, `deleteEvent`

**Problem:** No verification that user has permission to modify the event's family.

**Recommendation:** Add authorization checks:
```java
if (!hasPermissionToModifyEvent(eventId, currentUserId)) {
    throw new UnauthorizedException("Not authorized to modify this event");
}
```

### 22. **SQL Injection Risk (Low)**

**Location:** Repository queries use `@Param`, which is safe, but worth noting.

**Status:** ✅ Safe - using parameterized queries.

---

## Performance Recommendations

### 23. **Database Query Optimization**

**Current:** Multiple queries per recurring event  
**Recommendation:** Use JOINs or batch fetching:
```sql
SELECT e.*, ex.occurrence_date, ex.modified_event_id
FROM calendar_event e
LEFT JOIN calendar_event_exception ex ON e.id = ex.event_id
WHERE e.family_id = ? AND e.recurring_type IS NOT NULL
```

### 24. **Frontend State Management**

**Problem:** Re-fetching all events after every operation.

**Recommendation:** Implement optimistic updates:
- Update local state immediately
- Revert on error
- Reduces perceived latency

---

## Code Quality Improvements

### 25. **Extract Complex Logic**

**Location:** `getEventsForDateRange` is 70+ lines

**Recommendation:** Extract methods:
```java
private List<CalendarEvent> generateInstancesForRecurringEvents(...)
private void addModifiedEventsFromExceptions(...)
private void addBaseEventIfNeeded(...)
```

### 26. **Use Builder Pattern**

**Location:** `createEvent` has 13+ parameters

**Recommendation:** Use builder pattern:
```java
CalendarEvent.builder()
    .familyId(familyId)
    .title(title)
    .build();
```

### 27. **Constants for Magic Values**

**Location:** Multiple places with hardcoded values

**Recommendation:** Extract to constants:
```java
private static final int DEFAULT_RECURRING_INTERVAL = 1;
private static final int MAX_RECURRING_ITERATIONS = 1000;
```

---

## Documentation Needs

### 28. **API Documentation**

**Problem:** No OpenAPI/Swagger documentation for new endpoints.

**Recommendation:** Add `@Operation` annotations to controller methods.

### 29. **Architecture Decision Record (ADR)**

**Recommendation:** Document why exception-based approach was chosen over alternatives.

---

## Positive Highlights

1. ✅ **Good use of domain modeling** - `OccurrenceScope` enum is clear
2. ✅ **Proper use of transactions** - Methods are transactional
3. ✅ **Good frontend state management** - Using React hooks appropriately
4. ✅ **Responsive UI** - Dialog adapts to screen size
5. ✅ **Clear separation of concerns** - Frontend/backend separation is good

---

## Priority Action Items

### Must Fix Before Production:
1. 🔴 Fix N+1 query problem (#1)
2. 🔴 Add transaction rollback handling (#2)
3. 🔴 Fix race condition in exception creation (#3)
4. 🔴 Handle existing modified events when updating exceptions (#5)
5. 🟠 Add validation (#8)
6. 🟠 Fix type safety in frontend (#6)

### Should Fix Soon:
7. 🟠 Add logging (#10)
8. 🟠 Add authorization checks (#21)
9. 🟡 Add unit tests (#19)
10. 🟡 Extract reusable components (#11)

### Nice to Have:
11. 🟡 Improve cache strategy (#18)
12. 🟡 Add API documentation (#28)
13. 🟡 Extract constants (#27)

---

## Conclusion

The implementation demonstrates a solid understanding of the requirements and follows good practices in many areas. However, the critical issues (especially N+1 queries and race conditions) must be addressed before this can be considered production-ready.

**Estimated effort to address critical issues:** 2-3 days  
**Estimated effort for all major issues:** 1 week  
**Risk level:** Medium-High (due to data consistency concerns)

**Recommendation:** Address critical issues, add comprehensive tests, then proceed with deployment.
