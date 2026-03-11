# Code Review: Phase 1 - CalendarView Refactoring

**Reviewer:** Senior Developer  
**Date:** 2026-01-18  
**Scope:** Phase 1 - Extract Existing Sub-Components  
**Status:** ✅ **APPROVED WITH MINOR RECOMMENDATIONS**

---

## 📊 Executive Summary

**Overall Assessment:** ✅ **GOOD** - Solid refactoring work with proper separation of concerns. The extraction is clean and maintains functionality. Minor improvements recommended before Phase 2.

**Key Metrics:**
- CalendarView.tsx: 3,203 → 1,378 lines (57% reduction) ✅
- Components extracted: 4 (EventForm, WeekView, MonthView, CategoryManager) ✅
- Linter errors: 0 ✅
- Type safety: Good ✅
- Test coverage: Manual smoke test passed ✅

---

## ✅ Strengths

### 1. **Clean Component Extraction**
- ✅ All components properly extracted to separate files
- ✅ Type definitions exported correctly (`export type`)
- ✅ Components exported correctly (`export function`)
- ✅ Imports are clean and organized
- ✅ No circular dependencies

### 2. **Type Safety**
- ✅ All Props types are properly defined and exported
- ✅ TypeScript types are consistent across components
- ✅ No `any` types used
- ✅ Proper null/undefined handling

### 3. **File Organization**
- ✅ Logical directory structure (`components/`)
- ✅ Consistent naming conventions
- ✅ Clear separation of concerns

### 4. **Code Quality**
- ✅ No linter errors
- ✅ Consistent code style
- ✅ Proper use of React hooks
- ✅ Good component composition

---

## ⚠️ Issues & Recommendations

### 🔴 **CRITICAL** (Must Fix Before Push)

**None** - No critical issues found.

---

### 🟡 **HIGH PRIORITY** (Should Fix Before Phase 2)

#### 1. **Code Duplication: `getEventsForDay` Logic**
**Location:** `WeekView.tsx` (lines 36-58) and `MonthView.tsx` (lines 26-51)

**Issue:** Nearly identical logic for filtering events by day exists in both components.

**Impact:** 
- Maintenance burden (changes must be made in two places)
- Risk of inconsistencies
- Code bloat

**Recommendation:**
```typescript
// Create: frontend/src/features/calendar/utils/eventFilters.ts
export function getEventsForDay(
  events: CalendarEventResponse[],
  day: Date | number,
  year?: number,
  month?: number
): CalendarEventResponse[] {
  // Unified logic here
}
```

**Priority:** High - Should be extracted before Phase 2 to avoid further duplication.

---

#### 2. **Unused Function: `formatTime` in WeekView**
**Location:** `WeekView.tsx` line 77

**Issue:** Function is defined but never used.

**Code:**
```typescript
const formatTime = (date: Date): string => {
  return date.toLocaleTimeString("sv-SE", { hour: "2-digit", minute: "2-digit" });
};
```

**Recommendation:** Remove unused function.

**Priority:** Medium - Cleanup, no functional impact.

---

#### 3. **Large Inline Type Definition in EventForm**
**Location:** `EventForm.tsx` lines 12-28

**Issue:** The `onSave` callback has a large inline type definition that makes the Props type hard to read.

**Current:**
```typescript
onSave: (eventData: {
  title: string;
  startDateTime: string;
  // ... 15 more fields
}) => void;
```

**Recommendation:**
```typescript
// Create: frontend/src/features/calendar/types/eventForm.ts
export type EventFormData = {
  title: string;
  startDateTime: string;
  endDateTime: string | null;
  // ... rest of fields
};

// Then in EventFormProps:
onSave: (eventData: EventFormData) => void;
```

**Priority:** Medium - Improves readability and reusability.

---

### 🟢 **LOW PRIORITY** (Nice to Have)

#### 4. **Alert/Confirm Usage**
**Location:** 
- `EventForm.tsx` line 159: `alert("Välj en typ för återkommande event.")`
- `CategoryManager.tsx` line 72: `confirm("Är du säker...")`

**Issue:** Using browser `alert()` and `confirm()` is not ideal for UX.

**Recommendation:** Consider implementing a proper modal/dialog component in a future phase. Not blocking for Phase 1.

**Priority:** Low - Can be addressed in Phase 4 or later.

---

#### 5. **Magic Numbers and Constants**
**Location:** Multiple files

**Issues:**
- `CATEGORY_COLORS` is defined in `CategoryManager.tsx` but could be shared
- Hard-coded values like `maxTasksToShow = 2` in MonthView
- Date range limits like `maxDays = 365` in CalendarView

**Recommendation:** Extract to constants file:
```typescript
// frontend/src/features/calendar/constants.ts
export const CATEGORY_COLORS = [...];
export const MAX_TASKS_TO_SHOW = 2;
export const MAX_RECURRING_DAYS = 365;
```

**Priority:** Low - Can be done incrementally.

---

#### 6. **Inline Styles**
**Location:** All component files

**Issue:** Extensive use of inline styles makes components harder to read and maintain.

**Current State:** Acceptable for Phase 1 (no functional impact)

**Recommendation:** Consider CSS modules or styled-components in future phases. Not a blocker.

**Priority:** Low - Design decision, can be addressed later.

---

#### 7. **Missing JSDoc Comments**
**Location:** All component files

**Issue:** No JSDoc comments for exported components and types.

**Recommendation:** Add JSDoc comments for better IDE support and documentation:
```typescript
/**
 * EventForm component for creating and editing calendar events.
 * 
 * @param event - Existing event to edit, or null for new event
 * @param initialStartDate - Pre-filled start date (YYYY-MM-DD or YYYY-MM-DDTHH:mm)
 * @param categories - Available event categories
 * @param members - Available family members for participants
 * @param currentUserRole - Current user's role for permission checks
 * @param currentUserId - Current user's ID for permission checks
 * @param onSave - Callback when form is submitted
 * @param onDelete - Optional callback for deleting event
 * @param onCancel - Callback when form is cancelled
 */
export function EventForm({ ... }: EventFormProps) {
```

**Priority:** Low - Can be added incrementally.

---

## 🔍 Detailed Component Analysis

### **EventForm.tsx** (743 lines)

**Strengths:**
- ✅ Well-structured form with proper validation
- ✅ Good handling of date/time conversions
- ✅ Proper state management
- ✅ Good user experience (auto-fill end date, etc.)

**Issues:**
- ⚠️ Large component (743 lines) - Consider breaking into sub-components in future phases
- ⚠️ Complex date handling logic could be extracted to utilities
- ⚠️ Inline type definition for `onSave` callback (see recommendation #3)

**Recommendations:**
- Extract date conversion utilities (`isoToLocalDateTime`, `getDefaultEndDate`)
- Consider splitting into smaller sub-components (RecurringOptions, TaskOptions, etc.)

---

### **WeekView.tsx** (514 lines)

**Strengths:**
- ✅ Clean component structure
- ✅ Good event filtering logic
- ✅ Proper handling of overlapping events
- ✅ Good separation of all-day events, tasks, and hourly events

**Issues:**
- ⚠️ Unused `formatTime` function (line 77)
- ⚠️ Duplicated `getEventsForDay` logic (see recommendation #1)
- ⚠️ Complex overlapping event calculation could be extracted

**Recommendations:**
- Remove unused `formatTime` function
- Extract `getEventsForDay` to shared utility
- Consider extracting overlapping event calculation logic

---

### **MonthView.tsx** (333 lines)

**Strengths:**
- ✅ Clean component structure
- ✅ Good event filtering and display logic
- ✅ Proper handling of tasks vs. events
- ✅ Good truncation logic for long titles

**Issues:**
- ⚠️ Duplicated `getEventsForDay` logic (see recommendation #1)
- ⚠️ Magic number `maxTasksToShow = 2` should be a constant

**Recommendations:**
- Extract `getEventsForDay` to shared utility
- Extract magic numbers to constants

---

### **CategoryManager.tsx** (251 lines)

**Strengths:**
- ✅ Clean component structure
- ✅ Good error handling
- ✅ Proper state management
- ✅ Good user experience

**Issues:**
- ⚠️ `CATEGORY_COLORS` constant could be shared (used in EventForm dropdown too)
- ⚠️ Using browser `confirm()` (see recommendation #4)

**Recommendations:**
- Extract `CATEGORY_COLORS` to shared constants file
- Consider replacing `confirm()` with proper modal

---

### **CalendarView.tsx** (1,378 lines)

**Strengths:**
- ✅ Properly uses extracted components
- ✅ Clean imports
- ✅ Good separation of concerns maintained
- ✅ No leftover code from extraction

**Issues:**
- ⚠️ Still quite large (1,378 lines) - Will be addressed in Phase 2-3
- ⚠️ Helper functions like `formatDateTime`, `formatDateTimeRange`, `getAllDayEventDates` could be extracted to utilities
- ⚠️ Complex state management - Will be addressed in Phase 3

**Recommendations:**
- Extract date formatting utilities to shared file
- Continue with Phase 2-3 as planned

---

## 📋 Action Items

### **Before Push to Master:**
- [x] ✅ **ALL FIXED** - All high priority issues resolved

### **Completed Fixes:**
- [x] ✅ Extract `getEventsForDay` logic to shared utility (`utils/eventFilters.ts`)
- [x] ✅ Remove unused `formatTime` function from WeekView
- [x] ✅ Extract `EventFormData` type to separate file (`types/eventForm.ts`)
- [x] ✅ Extract date formatting utilities (`utils/dateFormatters.ts`)
- [x] ✅ Extract constants (`constants.ts` - CATEGORY_COLORS, MAX_TASKS_TO_SHOW_IN_MONTH, MAX_RECURRING_DAYS)

### **Future Improvements (Phase 4+):**
- [ ] Extract date formatting utilities
- [ ] Extract constants (CATEGORY_COLORS, magic numbers)
- [ ] Replace alert/confirm with proper modals
- [ ] Add JSDoc comments
- [ ] Consider CSS modules for styling

---

## 🎯 Verdict

**✅ APPROVED FOR PUSH TO MASTER - ALL ISSUES RESOLVED**

The refactoring is solid and maintains all functionality. The code is clean, well-organized, and follows React best practices. **All high priority issues have been fixed:**

1. ✅ `getEventsForDay` extracted to shared utility (`utils/eventFilters.ts`)
2. ✅ Unused `formatTime` function removed from WeekView
3. ✅ `EventFormData` type extracted to separate file (`types/eventForm.ts`)
4. ✅ Date formatting utilities extracted (`utils/dateFormatters.ts`)
5. ✅ Constants extracted (`constants.ts`)

**Recommendation:** ✅ **READY TO PUSH** - Code quality is now at production level.

---

## 📝 Notes

1. **No Breaking Changes:** All extracted components maintain the same API, ensuring backward compatibility.

2. **Test Coverage:** Manual smoke test passed. Consider adding unit tests for extracted components in future phases.

3. **Performance:** No performance regressions observed. The extraction actually improves code splitting potential.

4. **Maintainability:** Significant improvement in maintainability. Each component is now independently testable and modifiable.

5. **Next Steps:** Proceed with Phase 2 (Extract RollingView + Create Data Hooks) after addressing HIGH priority items.

---

**Reviewed by:** Senior Developer  
**Date:** 2026-01-18
