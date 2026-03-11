# Troubleshooting Task Completion 500 Error

## Problem
Getting 500 Internal Server Error when trying to mark a task as complete.

## Enhanced Logging
Debug logging has been enabled for:
- `com.familyapp` - All application code
- `org.springframework.transaction` - Transaction boundaries
- `org.springframework.cache` - Cache hits/misses

## Steps to Debug

1. **Restart backend** to apply new logging configuration
2. **Try to mark a task as complete** from frontend
3. **Copy the backend logs** (console output or docker logs)
4. **Look for**:
   - Exception stack traces
   - Cache hit/miss messages
   - Transaction rollback messages
   - Any "LazyInitializationException" or "Hibernate" errors

## Common Issues to Check

### 1. Lazy Loading Exception
**Symptom**: `LazyInitializationException: could not initialize proxy`
**Cause**: Cached entity is detached from persistence context
**Fix**: Ensure entities are loaded within transaction

### 2. Cache Returning Null
**Symptom**: `NullPointerException` when accessing cached member
**Cause**: Cache eviction or stale cache entry
**Fix**: Check cache eviction logic

### 3. Transaction Boundary Issue
**Symptom**: Entity not found or detached entity
**Cause**: Method called outside transaction
**Fix**: Ensure `@Transactional` is properly applied

### 4. Circular Dependency
**Symptom**: Bean creation error or proxy issues
**Cause**: CalendarService and FamilyMemberService dependency cycle
**Fix**: Use `@Lazy` annotation if needed

## What to Share

When reporting the issue, please include:
1. Full stack trace from backend logs
2. The exact request (eventId, memberId, occurrenceDate)
3. Any cache-related log messages
4. Transaction boundary logs
