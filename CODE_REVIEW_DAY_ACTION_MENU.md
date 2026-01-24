# Code Review: Day Action Menu & Month View Navigation
**Reviewer:** Senior Developer  
**Date:** 2025-01-27  
**Feature:** Day action menu for month view with navigation to rolling view  
**Files Reviewed:**
- `frontend/src/features/calendar/components/DayActionMenu.tsx` (NEW)
- `frontend/src/features/calendar/components/MonthView.tsx`
- `frontend/src/features/calendar/CalendarContainer.tsx`
- `frontend/src/features/calendar/components/RollingView.tsx`
- `frontend/src/features/calendar/components/EventForm.tsx`

---

## Executive Summary

**Overall Assessment:** ✅ **APPROVED with Minor Suggestions**

The implementation of the day action menu for month view is well-structured and provides a good user experience. The navigation to rolling view with automatic scrolling works correctly. The permission system is properly implemented. There are a few minor improvements that could be made for better maintainability and edge case handling.

**Critical Issues:** None  
**Major Issues:** None  
**Minor Issues:** 3 (code cleanup, edge cases, type safety)

---

## Strengths

### 1. **Clean Component Architecture** ⭐
The `DayActionMenu` component is well-separated and follows React best practices:
- Clear prop types
- Single responsibility (showing actions for a specific day)
- Proper event handling with stopPropagation
- Good accessibility with backdrop click handling

### 2. **Proper Permission Handling**
The permission logic is correctly implemented:
- PARENT and ASSISTANT can edit/delete all calendar events
- CHILD cannot edit/delete events
- Consistent with `EventForm` permission logic
- Proper filtering of tasks vs calendar events

### 3. **Good User Experience**
- Centered text on buttons for consistency
- Clean, minimal icons matching the app's design language
- Smooth navigation to rolling view with automatic scrolling
- Clear visual hierarchy

### 4. **Robust Scroll Implementation**
The scroll-to-date functionality in `RollingView`:
- Handles timezone issues correctly
- Implements retry mechanism for async loading
- Automatically loads more events if needed
- Proper cleanup and error handling

---

## Issues & Recommendations

### 🔴 Critical Issues
**None**

### 🟡 Major Issues
**None**

### 🟢 Minor Issues & Suggestions

#### 1. **Unused Code in DayActionMenu**
**Location:** `DayActionMenu.tsx:39-47`

**Issue:**
```typescript
// Sort events: non-tasks first, then by start time
const sortedEvents = [...editableEvents].sort((a, b) => {
  // Non-tasks before tasks
  if (a.isTask !== b.isTask) {
    return a.isTask ? 1 : -1;
  }
  // Then by start time
  return new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime();
});
```

**Problem:**
The sorting logic checks `a.isTask !== b.isTask`, but `editableEvents` has already been filtered to only include non-task events (`calendarEvents.filter(...)`). This means `a.isTask` and `b.isTask` will always be `false`, making the first condition redundant.

**Recommendation:**
Simplify the sort to only sort by start time:

```typescript
// Sort events by start time
const sortedEvents = [...editableEvents].sort((a, b) => {
  return new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime();
});
```

**Impact:** Low (performance improvement, code clarity)  
**Effort:** Low  
**Priority:** Medium

---

#### 2. **Unused Prop: `currentUserId`**
**Location:** `DayActionMenu.tsx:13, 25`

**Issue:**
The `currentUserId` prop is defined and passed to `DayActionMenu` but is never used in the component. It was likely intended for future permission checks but is not needed since PARENT and ASSISTANT can edit all events.

**Recommendation:**
Remove the unused prop:

```typescript
// Remove from type
type DayActionMenuProps = {
  // ... other props
  currentUserRole: "CHILD" | "ASSISTANT" | "PARENT" | null;
  // Remove: currentUserId: string | null;
};

// Remove from function parameters
export function DayActionMenu({
  // ... other params
  currentUserRole,
  // Remove: currentUserId,
}: DayActionMenuProps) {
```

And remove it from `CalendarContainer.tsx:336`.

**Impact:** Low (code cleanup)  
**Effort:** Low  
**Priority:** Low

---

#### 3. **Magic Timeout Values**
**Location:** `CalendarContainer.tsx:327-333`, `RollingView.tsx:364, 385, 393, 400, 407`

**Issue:**
Multiple hardcoded timeout values are used:
- `100ms` - delay before setting scrollToDate
- `2000ms` - delay before clearing scrollToDate
- `200ms` - retry delay
- `300ms`, `400ms`, `500ms`, `800ms` - various scroll delays

**Recommendation:**
Extract to named constants:

```typescript
// In RollingView.tsx or a constants file
const SCROLL_RETRY_DELAY_MS = 200;
const SCROLL_INITIAL_DELAY_MS = 150;
const SCROLL_AFTER_LOAD_DELAY_MS = 400;
const SCROLL_AFTER_API_LOAD_DELAY_MS = 800;
const MAX_SCROLL_RETRIES = 5;
```

**Impact:** Low (maintainability)  
**Effort:** Low  
**Priority:** Low

---

#### 4. **Potential Race Condition in Scroll Logic**
**Location:** `RollingView.tsx:375-395`

**Issue:**
When `targetDateIndex === -1` and `needsMoreEvents` is true, the code calls `onLoadMoreEvents()` and then waits 800ms before attempting to scroll. However, if the component unmounts or `scrollToDate` changes during this time, the scroll will still attempt to execute.

**Current code:**
```typescript
if (needsMoreEvents && onLoadMoreEvents) {
  onLoadMoreEvents().then(() => {
    setTimeout(() => attemptScroll(), 800);
  });
}
```

**Recommendation:**
Add cleanup for the timeout:

```typescript
if (needsMoreEvents && onLoadMoreEvents) {
  const scrollTimeout = setTimeout(() => {
    attemptScroll();
  }, 800);
  
  onLoadMoreEvents().then(() => {
    // Clear any pending timeout and scroll immediately
    clearTimeout(scrollTimeout);
    setTimeout(() => attemptScroll(), 100);
  }).catch((error) => {
    clearTimeout(scrollTimeout);
    console.error("Error loading more events:", error);
    setTimeout(() => attemptScroll(), 500);
  });
  
  // Cleanup function for useEffect
  return () => clearTimeout(scrollTimeout);
}
```

**Impact:** Low (edge case)  
**Effort:** Medium  
**Priority:** Low

---

#### 5. **Missing Error Handling for Delete Action**
**Location:** `DayActionMenu.tsx:145-151`

**Issue:**
The delete button calls `onDeleteEvent` but doesn't handle potential errors or show loading state:

```typescript
onClick={async () => {
  await onDeleteEvent(event.id);
  onClose();
}}
```

**Recommendation:**
Add error handling and loading state:

```typescript
const [deletingEventId, setDeletingEventId] = useState<string | null>(null);

// In button onClick:
onClick={async () => {
  if (deletingEventId) return; // Prevent double-click
  setDeletingEventId(event.id);
  try {
    await onDeleteEvent(event.id);
    onClose();
  } catch (error) {
    console.error("Error deleting event:", error);
    // Optionally show error message to user
  } finally {
    setDeletingEventId(null);
  }
}}

// In button style:
disabled={deletingEventId === event.id}
opacity={deletingEventId === event.id ? 0.5 : 0.6}
```

**Impact:** Medium (UX improvement)  
**Effort:** Medium  
**Priority:** Medium

---

#### 6. **Type Safety: Date Comparison**
**Location:** `RollingView.tsx:340`

**Issue:**
Comparing date strings directly:

```typescript
if (targetDateStr < todayStr) {
  return;
}
```

This works but is less explicit than comparing Date objects. However, since we're using ISO format (YYYY-MM-DD), string comparison works correctly.

**Analysis:**
This is actually fine for ISO date strings, but could be more explicit:

```typescript
const targetDateObj = new Date(targetDateStr);
const todayObj = new Date(todayStr);
if (targetDateObj < todayObj) {
  return;
}
```

**Impact:** Very Low (code clarity)  
**Effort:** Low  
**Priority:** Very Low

---

## Code Quality Metrics

| Metric | Score | Notes |
|--------|-------|-------|
| **Correctness** | ✅ 9/10 | Works correctly, minor edge cases |
| **Performance** | ✅ 9/10 | Good memoization, efficient filtering |
| **Maintainability** | ✅ 8/10 | Good structure, some magic numbers |
| **Testability** | ⚠️ 6/10 | No unit tests visible (should add tests) |
| **Error Handling** | ⚠️ 7/10 | Good in most places, could improve delete action |
| **Type Safety** | ✅ 9/10 | Good TypeScript usage, minor improvements possible |
| **Accessibility** | ✅ 8/10 | Good keyboard/click handling, could add ARIA labels |

---

## Testing Recommendations

### Unit Tests Needed:
1. **`DayActionMenu` component:**
   - Test that it filters out tasks correctly
   - Test permission filtering (PARENT/ASSISTANT can see all, CHILD cannot)
   - Test that events are sorted correctly
   - Test backdrop click closes menu
   - Test all button actions (create, edit, delete, navigate)

2. **Scroll functionality in `RollingView`:**
   - Test scroll to date that exists
   - Test scroll to date that doesn't exist (should load more)
   - Test scroll to past date (should not scroll)
   - Test retry mechanism
   - Test cleanup on unmount

3. **`MonthView` integration:**
   - Test that clicking a day opens DayActionMenu
   - Test that clicking an event doesn't open menu (should open DayActionMenu)
   - Test that filteredEvents are used correctly

### Integration Tests:
- Test full flow: Click day in month view → Menu opens → Click "Redigera event" → Form opens
- Test navigation: Click day → Menu opens → Click "Gå till rullande vy" → View switches and scrolls
- Test permissions: Different roles see different events in menu

---

## Security Considerations

✅ **No security issues identified**

The implementation correctly handles permissions:
- Permission checks are done on the frontend (for UX) but should be enforced on backend
- No sensitive data exposed
- Proper event filtering based on user role

**Note:** Ensure backend API enforces the same permission rules (PARENT/ASSISTANT can edit all events).

---

## Performance Considerations

### Current Performance:
- ✅ Efficient filtering using array methods
- ✅ Proper memoization in parent components
- ✅ No unnecessary re-renders
- ✅ Good use of React patterns

### Potential Optimizations:
1. **Memoize `DayActionMenu` filtering:**
   ```typescript
   const sortedEvents = useMemo(() => {
     const calendarEvents = dayEvents.filter(event => !event.isTask);
     const editableEvents = calendarEvents.filter(event => {
       return currentUserRole === "PARENT" || currentUserRole === "ASSISTANT";
     });
     return [...editableEvents].sort((a, b) => {
       return new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime();
     });
   }, [dayEvents, currentUserRole]);
   ```

2. **Debounce scroll attempts** if user rapidly navigates between dates

---

## Final Recommendations

### Must Fix (Before Merge):
- ✅ None (code is production-ready)

### Should Fix (Next Sprint):
1. Remove unused `currentUserId` prop
2. Simplify sorting logic (remove redundant task check)
3. Add error handling for delete action
4. Extract magic numbers to constants

### Nice to Have (Future):
1. Add loading state for delete action
2. Add unit tests for DayActionMenu
3. Add ARIA labels for better accessibility
4. Consider memoizing filtering logic
5. Add cleanup for scroll timeouts

---

## Conclusion

This is a **well-implemented feature** that improves the user experience significantly. The code is clean, follows React best practices, and handles edge cases reasonably well. The permission system is correctly implemented, and the navigation flow works smoothly.

**Recommendation:** ✅ **APPROVE** - Ready for merge with optional improvements in future iterations.

**Kudos:**
- Excellent separation of concerns
- Good user experience design
- Proper permission handling
- Robust scroll implementation

**Next Steps:**
1. Merge the code
2. Address minor issues in next sprint
3. Add unit tests
4. Monitor user feedback

---

**Reviewed by:** Senior Developer  
**Review Date:** 2025-01-27
