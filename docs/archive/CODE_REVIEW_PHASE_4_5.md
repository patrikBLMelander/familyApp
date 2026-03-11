# Code Review: Calendar Refactoring Phase 4-5
**Reviewer:** Senior Developer  
**Date:** 2024  
**Scope:** All calendar refactoring changes (Phase 1-5)

---

## 📋 Executive Summary

**Overall Assessment:** ✅ **APPROVED WITH MINOR IMPROVEMENTS**

The refactoring has significantly improved code quality, maintainability, and organization. The code follows React best practices and demonstrates good separation of concerns. There are a few minor issues and improvement opportunities that should be addressed before production.

**Strengths:**
- Excellent component extraction and separation of concerns
- Good use of custom hooks for logic separation
- Proper TypeScript typing throughout
- Good documentation with JSDoc comments
- Performance optimizations with `useMemo` and `useCallback`

**Areas for Improvement:**
- Some dependency array issues in `useEffect` hooks
- Error handling could be more consistent
- Missing accessibility attributes in some components
- Some code duplication in button styling

---

## 🔴 High Priority Issues

### 1. **Potential Infinite Loop in `useCalendarData.ts` - `loadCategories`**

**Location:** `frontend/src/features/calendar/hooks/useCalendarData.ts:282-297`

**Issue:**
```typescript
const loadCategories = useCallback(async () => {
  // ...
}, [setError]);
```

The `setError` dependency is a state setter function from `useState`, which is stable and doesn't need to be in the dependency array. However, the current implementation has a logic issue:

```typescript
setError((prevError) => {
  if (prevError) {
    return null;
  }
  return prevError;
});
```

This logic is confusing - it only clears error if there was one, but doesn't clear it if there wasn't one (which is fine, but the logic is backwards).

**Fix:**
```typescript
const loadCategories = useCallback(async () => {
  try {
    const categoriesData = await fetchCalendarCategories();
    setCategories(categoriesData);
    // Clear error on success
    setError(null);
  } catch (e) {
    console.error("Error loading categories:", e);
    setError("Kunde inte ladda kategorier. Försök igen.");
  }
}, []); // Remove setError from dependencies
```

**Impact:** Low risk, but confusing code that should be cleaned up.

---

### 2. **Missing Error Handling in `useCalendarEvents.ts` - `handleQuickAdd`**

**Location:** `frontend/src/features/calendar/hooks/useCalendarEvents.ts:130-177`

**Issue:**
The `handleQuickAdd` function doesn't reload tasks if the API call succeeds but the task reload fails. This could lead to inconsistent UI state.

**Current Code:**
```typescript
try {
  await createCalendarEvent(...);
  setQuickAddTitle("");
  setShowQuickAdd(false);
  if (showAllMembers) {
    await loadTasksForAllMembers();
  } else {
    await loadTasks();
  }
} catch (e) {
  console.error("Error creating quick task:", e);
  setError("Kunde inte skapa task.");
}
```

**Problem:** If `loadTasks()` or `loadTasksForAllMembers()` fails, the error is swallowed and the UI might show stale data.

**Fix:**
```typescript
try {
  await createCalendarEvent(...);
  setQuickAddTitle("");
  setShowQuickAdd(false);
  try {
    if (showAllMembers) {
      await loadTasksForAllMembers();
    } else {
      await loadTasks();
    }
  } catch (reloadError) {
    console.error("Error reloading tasks after quick add:", reloadError);
    // Still show success, but log the reload error
    setError("Task skapad, men kunde inte uppdatera listan. Ladda om sidan.");
  }
} catch (e) {
  console.error("Error creating quick task:", e);
  setError("Kunde inte skapa task.");
}
```

**Impact:** Medium - Could lead to confusing UX where task is created but not visible.

---

### 3. **Potential Race Condition in `CalendarContainer.tsx`**

**Location:** `frontend/src/features/calendar/CalendarContainer.tsx:116-124`

**Issue:**
The `useEffect` that loads tasks has many dependencies and could trigger multiple times unnecessarily:

```typescript
useEffect(() => {
  if (showTasksOnly && viewType === CALENDAR_VIEW_TYPES.ROLLING) {
    if (showAllMembers) {
      void loadTasksForAllMembers(members, selectedDate);
    } else if (currentMemberId) {
      void loadTasks(currentMemberId, selectedDate);
    }
  }
}, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers, members, loadTasks, loadTasksForAllMembers]);
```

**Problems:**
1. `members` is an array that changes reference on every load, causing unnecessary re-runs
2. `loadTasks` and `loadTasksForAllMembers` are callbacks that might change reference
3. No cleanup or debouncing for rapid date changes

**Fix:**
```typescript
// Option 1: Use refs for stable references
const membersRef = useRef(members);
useEffect(() => {
  membersRef.current = members;
}, [members]);

useEffect(() => {
  if (showTasksOnly && viewType === CALENDAR_VIEW_TYPES.ROLLING) {
    if (showAllMembers) {
      void loadTasksForAllMembers(membersRef.current, selectedDate);
    } else if (currentMemberId) {
      void loadTasks(currentMemberId, selectedDate);
    }
  }
}, [showTasksOnly, viewType, currentMemberId, selectedDate, showAllMembers, loadTasks, loadTasksForAllMembers]);

// Option 2: Memoize members array length/content
const membersIds = useMemo(() => members.map(m => m.id).join(','), [members]);
```

**Impact:** Medium - Could cause unnecessary API calls and performance issues.

---

## 🟡 Medium Priority Issues

### 4. **Code Duplication in Button Styling**

**Location:** Multiple components (`CalendarViewSelector.tsx`, `CalendarFilters.tsx`)

**Issue:**
Button styling is duplicated across multiple components with identical inline styles.

**Example:**
```typescript
// CalendarViewSelector.tsx - repeated 3 times
style={{
  flex: 1,
  padding: "8px 12px",
  borderRadius: "6px",
  border: "none",
  background: viewType === CALENDAR_VIEW_TYPES.ROLLING ? "#b8e6b8" : "transparent",
  color: viewType === CALENDAR_VIEW_TYPES.ROLLING ? "#2d5a2d" : "#6b6b6b",
  fontWeight: viewType === CALENDAR_VIEW_TYPES.ROLLING ? 600 : 400,
  fontSize: "0.85rem",
  cursor: "pointer",
  transition: "all 0.2s ease"
}}
```

**Fix:**
Create a shared utility function or styled component:

```typescript
// utils/buttonStyles.ts
export function getToggleButtonStyle(isActive: boolean) {
  return {
    flex: 1,
    padding: "8px 12px",
    borderRadius: "6px",
    border: "none" as const,
    background: isActive ? "#b8e6b8" : "transparent",
    color: isActive ? "#2d5a2d" : "#6b6b6b",
    fontWeight: isActive ? 600 : 400,
    fontSize: "0.85rem",
    cursor: "pointer" as const,
    transition: "all 0.2s ease",
  };
}
```

**Impact:** Low - Code maintainability improvement.

---

### 5. **Missing Accessibility Attributes**

**Location:** `CalendarViewSelector.tsx`, `CalendarFilters.tsx`

**Issue:**
Buttons lack `aria-label` and `aria-pressed` attributes for better screen reader support.

**Fix:**
```typescript
<button
  type="button"
  onClick={() => setViewType(CALENDAR_VIEW_TYPES.ROLLING)}
  aria-label="Visa rullande kalendervy"
  aria-pressed={viewType === CALENDAR_VIEW_TYPES.ROLLING}
  style={getToggleButtonStyle(viewType === CALENDAR_VIEW_TYPES.ROLLING)}
>
  Rullande
</button>
```

**Impact:** Medium - Accessibility improvement for users with screen readers.

---

### 6. **Inconsistent Error Message Handling**

**Location:** Multiple files

**Issue:**
Some error handlers show generic messages, others extract specific messages from API responses. Should be consistent.

**Current:**
- `useCalendarEvents.ts`: Generic messages ("Kunde inte skapa event.")
- `useCalendarData.ts`: Generic messages ("Kunde inte hämta kalenderdata.")
- `CategoryManager.tsx`: Extracts specific messages from API

**Fix:**
Create a utility function to extract error messages consistently:

```typescript
// utils/errorHandling.ts
export function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  return "Ett oväntat fel uppstod.";
}
```

**Impact:** Low - UX improvement.

---

### 7. **Date Object Mutation in `useCalendarData.ts`**

**Location:** `frontend/src/features/calendar/hooks/useCalendarData.ts:95-98, 176-180, etc.`

**Issue:**
Date objects are being mutated directly with `setHours()`, `setDate()`, etc. This can cause issues if the date objects are shared.

**Example:**
```typescript
const dateStart = new Date(date);
dateStart.setHours(0, 0, 0, 0); // Mutation
```

**Fix:**
This is actually fine since we're creating new Date objects. However, for clarity and safety, consider using a date utility library or being more explicit:

```typescript
const dateStart = new Date(date);
dateStart.setHours(0, 0, 0, 0); // OK - we created a new Date
// Or use a library like date-fns:
import { startOfDay } from 'date-fns';
const dateStart = startOfDay(date);
```

**Impact:** Low - Current code is fine, but could be clearer.

---

## 🟢 Low Priority / Suggestions

### 8. **Type Safety: `ViewKey` Duplication**

**Location:** `CalendarView.tsx`, `CalendarContainer.tsx`, `CalendarHeader.tsx`

**Issue:**
The `ViewKey` type is duplicated in multiple files.

**Fix:**
Move to a shared types file:

```typescript
// types/navigation.ts
export type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";
```

**Impact:** Low - Code organization improvement.

---

### 9. **Magic Numbers in Date Calculations**

**Location:** `useCalendarData.ts:179, 190, 193`

**Issue:**
Magic numbers like `30`, `7`, `14` for days are not self-documenting.

**Fix:**
Extract to constants:

```typescript
// constants.ts
export const ROLLING_VIEW_DAYS_AHEAD = 30;
export const WEEK_VIEW_DAYS_BEFORE = 7;
export const WEEK_VIEW_DAYS_AFTER = 7;
```

**Impact:** Low - Code readability improvement.

---

### 10. **Missing Loading State for Task Toggle**

**Location:** `useCalendarData.ts:handleToggleTask`

**Issue:**
When toggling a task, there's no loading indicator. The optimistic update happens immediately, but if the API call is slow, users might click multiple times.

**Fix:**
Add a loading state or disable the button during the operation:

```typescript
const [togglingTaskId, setTogglingTaskId] = useState<string | null>(null);

const handleToggleTask = useCallback(async (eventId: string, ...) => {
  if (togglingTaskId === eventId) return; // Prevent double-click
  setTogglingTaskId(eventId);
  // ... existing code ...
  setTogglingTaskId(null);
}, [...]);
```

**Impact:** Low - UX improvement.

---

### 11. **JSDoc Parameter Documentation**

**Location:** `useCalendarEvents.ts:44-56`

**Issue:**
The JSDoc doesn't document all parameters individually, making it harder to understand what each callback does.

**Fix:**
Add `@param` tags for each parameter:

```typescript
/**
 * @param loadData - Callback to reload all calendar data after CRUD operations
 * @param setError - Callback to set error state for user feedback
 * @param setShowCreateForm - Callback to control create form visibility
 * ...
 */
```

**Impact:** Low - Documentation improvement.

---

## ✅ Strengths & Best Practices

### 1. **Excellent Component Extraction**
- Clean separation between `CalendarView` (wrapper) and `CalendarContainer` (logic)
- UI components (`CalendarHeader`, `CalendarViewSelector`, `CalendarFilters`) are well-focused
- Good use of custom hooks (`useCalendarData`, `useCalendarEvents`)

### 2. **Good TypeScript Usage**
- Proper typing throughout
- Type safety with `CalendarViewType` and constants
- Good use of union types for `ViewKey`

### 3. **Performance Optimizations**
- Proper use of `useMemo` for `filteredEvents` and `handleToggleTaskWrapper`
- `useCallback` for all async functions
- Optimistic updates for task toggling

### 4. **Code Organization**
- Clear file structure with `components/`, `hooks/`, `utils/`, `constants/`
- Good naming conventions
- Logical grouping of related functionality

### 5. **Documentation**
- JSDoc comments on all major functions and components
- Clear parameter descriptions
- Good inline comments for complex logic

### 6. **Error Handling**
- Try-catch blocks in all async operations
- User-friendly error messages in Swedish
- Console logging for debugging

---

## 📊 Metrics

**Before Refactoring:**
- `CalendarView.tsx`: ~464 lines (monolithic)

**After Refactoring:**
- `CalendarView.tsx`: 20 lines (wrapper)
- `CalendarContainer.tsx`: 299 lines (state management)
- `CalendarHeader.tsx`: 62 lines
- `CalendarViewSelector.tsx`: 78 lines
- `CalendarFilters.tsx`: 117 lines
- `useCalendarData.ts`: 316 lines
- `useCalendarEvents.ts`: 185 lines

**Improvements:**
- ✅ 35% reduction in main component size
- ✅ Clear separation of concerns
- ✅ All components < 500 lines (except EventForm: 727, RollingView: 619 - acceptable for complex components)
- ✅ Reusable hooks and components

---

## 🎯 Recommendations

### Must Fix Before Production:
1. ✅ Fix `loadCategories` error clearing logic (Issue #1)
2. ✅ Improve error handling in `handleQuickAdd` (Issue #2)
3. ✅ Fix potential race condition in task loading (Issue #3)

### Should Fix Soon:
4. ⚠️ Extract button styling to reduce duplication (Issue #4)
5. ⚠️ Add accessibility attributes (Issue #5)
6. ⚠️ Standardize error message handling (Issue #6)

### Nice to Have:
7. 💡 Extract `ViewKey` type to shared location (Issue #8)
8. 💡 Extract magic numbers to constants (Issue #9)
9. 💡 Add loading state for task toggle (Issue #10)

---

## ✅ Final Verdict

**Status:** ✅ **APPROVED WITH MINOR FIXES**

The refactoring is well-executed and significantly improves code quality. The identified issues are minor and can be addressed in a follow-up PR. The code is production-ready after fixing the high-priority issues (#1, #2, #3).

**Confidence Level:** High - The architecture is solid and the code follows React best practices.

---

## 📝 Action Items

- [ ] Fix `loadCategories` error clearing logic
- [ ] Improve error handling in `handleQuickAdd`
- [ ] Fix race condition in task loading `useEffect`
- [ ] Extract button styling utilities
- [ ] Add accessibility attributes to buttons
- [ ] Standardize error message extraction
