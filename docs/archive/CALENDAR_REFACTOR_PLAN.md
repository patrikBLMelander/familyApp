# CalendarView.tsx Refactoring Plan

## Overview
**Current State**: 3,203 lines, monolithic component with 51 useState/useEffect hooks  
**Target State**: Modular, maintainable components with clear separation of concerns  
**Estimated Total Effort**: 2-3 days

---

## 🎯 Natural Push Points (Testable Milestones)

### ✅ **Push Point 1: Extract Existing Sub-Components** (Low Risk)
**Status**: Ready to push after completion  
**Risk**: Very Low - These are already separate components, just moving to files  
**Test**: Verify all views still work (week, month, event form, category manager)

### ✅ **Push Point 2: Extract RollingView + Create Data Hooks** (Medium Risk)
**Status**: Ready to push after completion  
**Risk**: Medium - RollingView extraction + data loading hooks  
**Test**: Verify rolling view works, data loads correctly, no regressions

### ✅ **Push Point 3: Create Container + Refactor Main Component** (Medium Risk)
**Status**: Ready to push after completion  
**Risk**: Medium - State management refactoring  
**Test**: Verify all functionality works, state management is correct

### ✅ **Push Point 4: Extract UI Components** (Low Risk)
**Status**: Ready to push after completion  
**Risk**: Low - Just UI extraction, logic stays same  
**Test**: Verify UI looks and works the same

### ✅ **Push Point 5: Final Cleanup** (Low Risk)
**Status**: Ready to push after completion  
**Risk**: Very Low - Just cleanup  
**Test**: Final smoke test

---

## 📋 Detailed Task List

### **Phase 1: Extract Existing Sub-Components** (Push Point 1)
**Effort**: 2-3 hours  
**Risk**: Very Low  
**Files to Create**:
- `frontend/src/features/calendar/components/EventForm.tsx`
- `frontend/src/features/calendar/components/WeekView.tsx`
- `frontend/src/features/calendar/components/MonthView.tsx`
- `frontend/src/features/calendar/components/CategoryManager.tsx`

**Tasks**:
1. ✅ Create `components/` directory in calendar feature
2. ✅ Copy `EventForm` component to `EventForm.tsx`
   - Extract from CalendarView.tsx (lines ~1407-2129)
   - Move all related types and constants
   - Update imports in CalendarView.tsx
3. ✅ Copy `WeekView` component to `WeekView.tsx`
   - Extract from CalendarView.tsx (lines ~2130-2640)
   - Move all related types
   - Update imports in CalendarView.tsx
4. ✅ Copy `MonthView` component to `MonthView.tsx`
   - Extract from CalendarView.tsx (lines ~2641-2977)
   - Move all related types
   - Update imports in CalendarView.tsx
5. ✅ Copy `CategoryManager` component to `CategoryManager.tsx`
   - Extract from CalendarView.tsx (lines ~2978-3053)
   - Move all related types and constants
   - Update imports in CalendarView.tsx
6. ✅ Test: Verify all views work (week, month, event form, category manager)
7. ✅ **PUSH TO MASTER** - Low risk, easy to verify

---

### **Phase 2: Extract RollingView + Create Data Hooks** (Push Point 2)
**Effort**: 4-5 hours  
**Risk**: Medium  
**Files to Create**:
- `frontend/src/features/calendar/components/RollingView.tsx`
- `frontend/src/features/calendar/hooks/useCalendarData.ts`
- `frontend/src/features/calendar/hooks/useCalendarEvents.ts`

**Tasks**:
1. ✅ Create `hooks/` directory in calendar feature
2. ✅ Extract RollingView component
   - Find rolling view JSX in CalendarView (around lines 588-1288)
   - Create `RollingView.tsx` component
   - Move all rolling view logic
   - Update CalendarView to use RollingView
3. ✅ Create `useCalendarData.ts` hook
   - Extract `loadData()` function
   - Extract `loadTasks()` function
   - Extract `loadTasksForAllMembers()` function
   - Extract `loadCurrentMember()` function
   - Return: `{ events, categories, members, tasksWithCompletion, tasksByMember, loading, error, loadData, loadTasks, loadTasksForAllMembers }`
4. ✅ Create `useCalendarEvents.ts` hook
   - Extract `handleCreateEvent()` function
   - Extract `handleUpdateEvent()` function
   - Extract `handleDeleteEvent()` function
   - Extract `handleQuickAdd()` function
   - Return: `{ handleCreateEvent, handleUpdateEvent, handleDeleteEvent, handleQuickAdd }`
5. ✅ Update CalendarView to use new hooks
6. ✅ Test: Verify rolling view works, data loads correctly, CRUD operations work
7. ✅ **PUSH TO MASTER** - Medium risk, but testable

---

### **Phase 3: Create Container + Refactor Main Component** (Push Point 3)
**Effort**: 3-4 hours  
**Risk**: Medium  
**Files to Create**:
- `frontend/src/features/calendar/CalendarContainer.tsx`

**Tasks**:
1. ✅ Create `CalendarContainer.tsx`
   - Move all state management from CalendarView
   - Move all useEffect hooks
   - Move view type logic
   - Move filter logic (showTasksOnly, showAllMembers, etc.)
   - Use hooks from Phase 2
   - Render appropriate view component based on viewType
2. ✅ Refactor `CalendarView.tsx` to be thin wrapper
   - Just renders CalendarContainer
   - Passes onNavigate prop through
3. ✅ Test: Verify all functionality works
   - View switching (rolling/week/month)
   - Filters work
   - Event CRUD works
   - Task completion works
   - Category management works
4. ✅ **PUSH TO MASTER** - Medium risk, but comprehensive testing

---

### **Phase 4: Extract UI Components** (Push Point 4)
**Effort**: 2-3 hours  
**Risk**: Low  
**Files to Create**:
- `frontend/src/features/calendar/components/CalendarFilters.tsx`
- `frontend/src/features/calendar/components/CalendarViewSelector.tsx`
- `frontend/src/features/calendar/components/CalendarHeader.tsx`

**Tasks**:
1. ✅ Create `CalendarFilters.tsx`
   - Extract filter UI (showTasksOnly toggle, showAllMembers toggle, member selector)
   - Props: `{ showTasksOnly, setShowTasksOnly, showAllMembers, setShowAllMembers, currentMemberId, setCurrentMemberId, members }`
2. ✅ Create `CalendarViewSelector.tsx`
   - Extract view type selector (rolling/week/month buttons)
   - Props: `{ viewType, setViewType }`
3. ✅ Create `CalendarHeader.tsx`
   - Extract header with navigation and actions
   - Props: `{ onNavigate, onOpenCategoryManager, onOpenQuickAdd, currentUserRole }`
4. ✅ Update CalendarContainer to use new UI components
5. ✅ Test: Verify UI looks and works the same
6. ✅ **PUSH TO MASTER** - Low risk, just UI extraction

---

### **Phase 5: Final Cleanup** (Push Point 5)
**Effort**: 1-2 hours  
**Risk**: Very Low  
**Tasks**:
1. ✅ Remove unused imports
2. ✅ Remove unused code
3. ✅ Add JSDoc comments to all components and hooks
4. ✅ Optimize component structure
5. ✅ Verify file sizes (target: < 500 lines per file)
6. ✅ Final smoke test
7. ✅ **PUSH TO MASTER** - Very low risk

---

## 📊 Expected Results

### Before
- `CalendarView.tsx`: 3,203 lines
- 51 useState/useEffect hooks
- Impossible to test
- Hard to maintain

### After
- `CalendarView.tsx`: ~50 lines (thin wrapper)
- `CalendarContainer.tsx`: ~200 lines (state management)
- `RollingView.tsx`: ~400 lines
- `WeekView.tsx`: ~500 lines
- `MonthView.tsx`: ~350 lines
- `EventForm.tsx`: ~700 lines
- `CategoryManager.tsx`: ~100 lines
- `CalendarFilters.tsx`: ~100 lines
- `CalendarViewSelector.tsx`: ~50 lines
- `CalendarHeader.tsx`: ~100 lines
- `useCalendarData.ts`: ~200 lines
- `useCalendarEvents.ts`: ~150 lines

**Total**: ~3,000 lines (same functionality, better organized)

---

## 🧪 Testing Strategy

### After Each Phase
1. **Manual Testing**:
   - Test all three views (rolling, week, month)
   - Test event CRUD (create, edit, delete)
   - Test task completion
   - Test filters
   - Test category management
   - Test quick add
   - Test date navigation

2. **Regression Testing**:
   - Verify no functionality is broken
   - Verify UI looks the same
   - Verify performance is same or better

3. **Code Review**:
   - Review extracted components
   - Verify proper separation of concerns
   - Check for any code smells

---

## 🚀 Execution Order

1. **Start with Phase 1** (Lowest risk, easiest to verify)
2. **Push to master** after Phase 1
3. **Continue with Phase 2** (Medium risk, but testable)
4. **Push to master** after Phase 2
5. **Continue with Phase 3** (Medium risk, comprehensive testing)
6. **Push to master** after Phase 3
7. **Continue with Phase 4** (Low risk, UI extraction)
8. **Push to master** after Phase 4
9. **Finish with Phase 5** (Very low risk, cleanup)
10. **Final push to master**

---

## 📝 Notes

- Each phase is designed to be **independently testable**
- Each push point is a **safe checkpoint** where functionality is verified
- If something breaks, we can **revert to last push point** easily
- **No big bang refactoring** - incremental improvements
- **Backward compatible** - no API changes, just internal structure

---

## ✅ Success Criteria

- [ ] All components are < 500 lines
- [ ] All functionality works as before
- [ ] No performance regression
- [ ] Code is more maintainable
- [ ] Components are testable
- [ ] Clear separation of concerns
