# Pre-Push Checklist: Adult Dashboard Redesign

**Date:** 2026-02-08  
**Feature:** Adult Dashboard Redesign + Pet Support for Adults

---

## ✅ Code Quality

- [x] **Linter Errors:** Only 2 warnings (unused fields) - non-critical
- [x] **TypeScript:** No type errors
- [x] **Compilation:** Backend compiles successfully
- [x] **Code Review:** Completed (see `CODE_REVIEW_ADULT_DASHBOARD.md`)

---

## ✅ Database Migration

- [x] **Migration File:** `V34__add_pet_enabled.sql` exists
- [x] **Migration Content:** 
  ```sql
  ALTER TABLE family_member ADD COLUMN pet_enabled BOOLEAN DEFAULT FALSE;
  ```
- [x] **Backward Compatible:** Yes - default FALSE, nullable
- [x] **No Breaking Changes:** Existing members will have `pet_enabled = false`

---

## ✅ Backend Changes

### Modified Files:
- [x] `FamilyMemberController.java` - Added `petEnabled` to response, new endpoint
- [x] `FamilyMemberService.java` - Added `updatePetSettings`, cache eviction
- [x] `FamilyMemberEntity.java` - Added `petEnabled` field
- [x] `FamilyMember.java` (domain) - Added `petEnabled` field
- [x] `PetController.java` - Allow PARENT role for pet operations
- [x] `PetService.java` - Allow PARENT role
- [x] `XpController.java` - Return 404 instead of exception
- [x] `XpService.java` - Allow PARENT role
- [x] `pets.ts` (API client) - Handle 404 gracefully
- [x] `xp.ts` (API client) - Handle 404 gracefully
- [x] `familyMembers.ts` (API client) - Added `petEnabled` and `updatePetSettings`

### Security:
- [x] **Role Validation:** PARENT role properly validated
- [x] **Permission Checks:** `updatePetSettings` checks family membership
- [x] **Cache Eviction:** Properly evicts caches when pet settings change
- [x] **Error Handling:** Improved error messages

---

## ✅ Frontend Changes

### New Files:
- [x] `AdultDashboard.tsx` - New dashboard component (1478 lines)
- [x] `utils/taskSorting.ts` - Utility function for task sorting

### Modified Files:
- [x] `App.tsx` - Route to `AdultDashboard` for parents
- [x] `FamilyMembersView.tsx` - Added pet settings UI for adults

### Features:
- [x] **Tab Navigation:** Calendar, Todos, Lists
- [x] **Pet Integration:** Conditional rendering based on `petEnabled`
- [x] **Calendar View:** Rolling calendar with infinite scroll
- [x] **Todo List:** Today's tasks with quick-add
- [x] **Lists Overview:** Shows all todo lists with colors

### Code Quality:
- [x] **Dependency Arrays:** Fixed in `loadPetData`
- [x] **Code Duplication:** Extracted sorting logic
- [x] **Error Handling:** Consistent error handling

---

## ⚠️ Known Issues / Technical Debt

1. **Component Size:** `AdultDashboard.tsx` is 1478 lines - consider splitting in future
2. **Console Statements:** 14 console.error/debug statements - should be wrapped in dev-only checks
3. **Hardcoded Strings:** Swedish strings hardcoded - no i18n support yet
4. **Linter Warnings:** 2 unused field warnings (non-critical)

**Status:** ✅ Acceptable for production - can be addressed in future iterations

---

## ✅ Testing Checklist

### Manual Testing:
- [x] **QR Code Linking:** Works correctly
- [x] **Pet Enable/Disable:** Works in FamilyMembersView
- [x] **Pet Visibility:** Only shows when `petEnabled = true`
- [x] **Pet Feeding:** Works for adults with pets enabled
- [x] **Dashboard Tabs:** All tabs work correctly
- [x] **Calendar Infinite Scroll:** Works correctly
- [x] **Task Toggle:** Works and updates pet data
- [x] **Cache Eviction:** Works when pet settings change

### Edge Cases:
- [x] **No Pet:** Dashboard shows correctly without pet
- [x] **Pet Disabled:** Pet section hidden correctly
- [x] **404 Errors:** Handled gracefully (no pet/XP data)
- [x] **Old Members:** Migration handles existing members correctly

---

## ✅ Breaking Changes

**None** - All changes are backward compatible:
- New column has default value
- Existing API endpoints unchanged
- New optional fields in responses
- New optional endpoints

---

## ✅ Rollback Plan

If issues occur:

1. **Database Rollback:**
   ```sql
   ALTER TABLE family_member DROP COLUMN pet_enabled;
   ```

2. **Code Rollback:**
   - Revert to previous commit
   - Remove `AdultDashboard.tsx` routing
   - Restore old `Dashboard` component

3. **Cache Clear:**
   - May need to clear application cache
   - Restart backend service

---

## 📋 Deployment Steps

1. **Pre-Deployment:**
   - [ ] Review all changes
   - [ ] Ensure all tests pass (if applicable)
   - [ ] Backup database

2. **Deployment:**
   - [ ] Deploy backend (migration will run automatically)
   - [ ] Deploy frontend
   - [ ] Verify migration ran successfully

3. **Post-Deployment:**
   - [ ] Test QR code linking
   - [ ] Test pet enable/disable
   - [ ] Test adult dashboard
   - [ ] Monitor error logs

---

## ✅ Final Checklist

- [x] All code changes reviewed
- [x] Migration tested (default value works)
- [x] No breaking changes
- [x] Backward compatible
- [x] Security checks passed
- [x] Error handling improved
- [x] Manual testing completed

---

## 🚀 Ready for Production?

**Status:** ✅ **YES** - Ready to push to production

**Recommendations:**
1. Commit all changes with descriptive commit message
2. Deploy during low-traffic period if possible
3. Monitor error logs after deployment
4. Have rollback plan ready (just in case)

**Estimated Risk:** 🟢 **LOW** - All changes are backward compatible and well-tested

---

## 📝 Commit Message Suggestion

```
feat: Adult Dashboard Redesign with Pet Support

- Redesign adult dashboard with tab-based navigation (Calendar, Todos, Lists)
- Add pet support for adults (opt-in via petEnabled flag)
- Add rolling calendar view with infinite scroll
- Add quick-add task functionality
- Add todo lists overview
- Backend: Add petEnabled column to family_member table
- Backend: Allow PARENT role for pet and XP operations
- Backend: Add updatePetSettings endpoint
- Frontend: Add pet settings UI in FamilyMembersView
- Fix: Improve error handling for 404 responses
- Fix: Fix dependency array issues in React hooks
- Refactor: Extract duplicate sorting logic to utility

Breaking Changes: None
Migration: V34__add_pet_enabled.sql
```
