# Code Review: Digital Wallet System
**Reviewer:** Senior Developer  
**Date:** 2026-02-08  
**Scope:** Complete wallet implementation (backend + frontend)

---

## 🎯 Executive Summary

**Overall Assessment:** ⚠️ **Good foundation, but critical issues need addressing**

The implementation demonstrates solid understanding of domain-driven design and clean architecture principles. However, there are **critical concurrency issues**, **security concerns**, and **data consistency risks** that must be addressed before production deployment.

**Priority Issues:**
1. 🔴 **CRITICAL:** Race conditions in balance updates (no optimistic/pessimistic locking)
2. 🔴 **CRITICAL:** Missing validation for goal ownership in `allocateToSavingsGoals`
3. 🟡 **HIGH:** Duplicate validation logic between controller and service
4. 🟡 **HIGH:** No transaction rollback handling for partial failures
5. 🟢 **MEDIUM:** Missing input sanitization and length limits
6. 🟢 **MEDIUM:** Inconsistent error handling patterns

---

## 🔴 Critical Issues

### 1. Race Conditions & Concurrency (CRITICAL)

**Location:** `WalletService.java` - All methods that update balance

**Problem:**
```java
// Line 128-130: addAllowance
wallet.setBalance(wallet.getBalance() + amount);
wallet.setUpdatedAt(OffsetDateTime.now());
walletRepository.save(wallet);
```

**Issue:** No optimistic or pessimistic locking. If two requests update the balance simultaneously:
- Request A reads balance = 100
- Request B reads balance = 100
- Request A writes balance = 150
- Request B writes balance = 120
- **Result: Lost update!** Should be 170, but becomes 120.

**Impact:** 
- Money can disappear or be duplicated
- Savings goal allocations can exceed limits
- Data corruption in production

**Solution:**
```java
@Entity
@Table(name = "child_wallet")
public class ChildWalletEntity {
    @Version
    private Long version; // Add optimistic locking
    
    // Or use pessimistic locking in service:
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM ChildWalletEntity w WHERE w.id = :id")
    ChildWalletEntity findByIdForUpdate(@Param("id") UUID id);
}
```

**Recommendation:** Implement optimistic locking with `@Version` field. Add retry logic for `OptimisticLockException`.

---

### 2. Missing Authorization Check (CRITICAL)

**Location:** `WalletService.allocateToSavingsGoals()` - Line 301-365

**Problem:**
```java
public void allocateToSavingsGoals(UUID memberId, List<SavingsGoalAllocation> allocations) {
    // ... 
    for (var allocation : allocations) {
        var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                .orElseThrow(...);
        // ❌ NO CHECK: Does this goal belong to memberId?
        goalEntity.setCurrentAmount(goalEntity.getCurrentAmount() + allocation.amount());
    }
}
```

**Issue:** Unlike `recordExpense()` (which checks ownership at line 232), `allocateToSavingsGoals()` doesn't verify that goals belong to the member.

**Impact:** A child could potentially allocate money to another child's savings goals if they know the goal ID.

**Solution:**
```java
if (!goalEntity.getMember().getId().equals(memberId)) {
    throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
}
```

**Recommendation:** Add ownership validation immediately after fetching the goal entity.

---

### 3. Transaction Atomicity Risk (HIGH)

**Location:** `WalletService.addAllowance()`, `recordExpense()`, `allocateToSavingsGoals()`

**Problem:** Multiple database operations without proper transaction boundaries:
1. Save transaction
2. Update wallet balance
3. Update multiple savings goals (in loop)
4. Save notifications

If step 3 fails partway through, the transaction and balance are already updated, but some goals aren't.

**Current State:** `@Transactional` is on class level, which helps, but:
- No explicit rollback strategy
- No handling of partial failures in loops
- If `savingsGoalRepository.save()` fails on goal #3 of 5, goals #1-2 are already updated

**Solution:**
```java
@Transactional(rollbackFor = Exception.class)
public void allocateToSavingsGoals(...) {
    // Validate ALL goals first
    List<SavingsGoalEntity> validatedGoals = new ArrayList<>();
    for (var allocation : allocations) {
        var goal = validateAndGetGoal(allocation, memberId);
        validatedGoals.add(goal);
    }
    
    // Then perform all updates
    // If any fails, entire transaction rolls back
}
```

**Recommendation:** Validate all entities before any updates. Consider using `@Transactional(rollbackFor = Exception.class)` explicitly.

---

## 🟡 High Priority Issues

### 4. Duplicate Validation Logic

**Location:** `WalletController.java` and `WalletService.java`

**Problem:**
```java
// Controller line 75-77
if (request.savingsGoalAllocations() == null || request.savingsGoalAllocations().isEmpty()) {
    throw new IllegalArgumentException("Minst ett sparmål måste anges");
}

// Service line 302-304 (same check)
if (allocations == null || allocations.isEmpty()) {
    throw new IllegalArgumentException("Minst ett sparmål måste anges");
}
```

**Issue:** Validation is duplicated. Controller should do basic request validation (null checks, format), service should do business validation.

**Recommendation:** 
- Controller: Validate request structure (null, empty, basic format)
- Service: Validate business rules (ownership, amounts, limits)

---

### 5. Missing Input Validation & Sanitization

**Location:** Multiple endpoints

**Issues:**
- No max length on `description` fields (SQL injection risk if not using parameterized queries, but also UX issue)
- No validation that `amount` isn't negative in controller (only in service)
- No validation that `targetAmount` for savings goals is reasonable (could be Integer.MAX_VALUE)
- No emoji validation (could be malicious strings)

**Recommendation:**
```java
@PostMapping("/allowance")
public ResponseEntity<Void> addAllowance(@Valid @RequestBody AddAllowanceRequest request, ...) {
    // Use Bean Validation
}

public record AddAllowanceRequest(
    @NotNull UUID childMemberId,
    @Min(1) @Max(1000000) int amount, // Reasonable limit
    @Size(max = 200) String description,
    List<SavingsGoalAllocationRequest> savingsGoalAllocations
) {}
```

---

### 6. Inconsistent Error Handling

**Location:** Frontend API client (`wallet.ts`)

**Problem:**
```typescript
// Some functions use handleJson (throws generic error)
export async function getWalletBalance(): Promise<WalletBalanceResponse> {
  const response = await fetch(...);
  return handleJson<WalletBalanceResponse>(response); // Throws generic error
}

// Others manually check response.ok
export async function addAllowance(...): Promise<void> {
  const response = await fetch(...);
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to add allowance: ${errorText}`);
  }
}
```

**Issue:** Inconsistent error handling makes it harder to handle errors uniformly in UI.

**Recommendation:** Use `handleJson` consistently, or create a wrapper that always extracts error messages from backend responses.

---

## 🟢 Medium Priority Issues

### 7. Missing Validation: Goal Over-allocation

**Location:** `WalletService.allocateToSavingsGoals()` - Line 337-364

**Problem:**
```java
// Updates goal without checking if it would exceed target
goalEntity.setCurrentAmount(goalEntity.getCurrentAmount() + allocation.amount());

// Only checks completion AFTER update
if (goalEntity.getCurrentAmount() >= goalEntity.getTargetAmount() && !goalEntity.isCompleted()) {
    goalEntity.setCompleted(true);
}
```

**Issue:** Unlike `recordExpense()` (which validates at line 236), `allocateToSavingsGoals()` doesn't prevent over-allocation. A user could allocate 1000 kr to a goal that only needs 100 kr.

**Recommendation:** Add validation similar to `recordExpense()`:
```java
int remaining = goalEntity.getTargetAmount() - goalEntity.getCurrentAmount();
if (allocation.amount() > remaining) {
    throw new IllegalArgumentException(
        String.format("Mål '%s' skulle överskridas. Max %d kr kvar.", 
            goalEntity.getName(), remaining)
    );
}
```

---

### 8. Performance: N+1 Query Problem

**Location:** `WalletService.getTransactionHistory()` - Line 379

**Problem:**
```java
var transactions = transactionRepository.findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(wallet.getId());
// This likely doesn't fetch categories, members, etc. eagerly
// Each transaction.toDomain() might trigger lazy loading
```

**Issue:** If transactions have relationships (category, createdByMember), each access might trigger a separate query.

**Recommendation:** Use `@EntityGraph` or `JOIN FETCH`:
```java
@Query("SELECT t FROM WalletTransactionEntity t " +
       "LEFT JOIN FETCH t.category " +
       "LEFT JOIN FETCH t.createdByMember " +
       "WHERE t.wallet.id = :walletId AND t.isDeleted = false " +
       "ORDER BY t.createdAt DESC")
List<WalletTransactionEntity> findByWalletIdWithRelations(@Param("walletId") UUID walletId);
```

---

### 9. Missing Audit Trail

**Location:** All write operations

**Issue:** No logging of financial transactions. If something goes wrong, hard to debug.

**Recommendation:** Add structured logging:
```java
@Slf4j
public class WalletService {
    public void addAllowance(...) {
        log.info("Adding allowance: childId={}, amount={}, givenBy={}", 
            childMemberId, amount, givenByMemberId);
        // ... operation
        log.info("Allowance added successfully: transactionId={}", transaction.getId());
    }
}
```

---

### 10. Frontend: Missing Error Boundaries

**Location:** All React components

**Issue:** If an API call fails unexpectedly, the entire component tree might crash.

**Example:** `WalletOverview.tsx` has some error handling, but `WalletDetailView.tsx` might not handle all edge cases.

**Recommendation:** Add error boundaries and consistent error handling:
```typescript
try {
  await loadData();
} catch (error) {
  // Log to error tracking service (Sentry, etc.)
  console.error("Error loading wallet data:", error);
  setError("Kunde inte ladda plånboksdata. Försök igen senare.");
  // Don't crash the entire app
}
```

---

## ✅ Positive Aspects

1. **Clean Architecture:** Good separation of concerns (Domain, Application, Infrastructure, API layers)
2. **Domain Modeling:** Well-designed domain entities with clear responsibilities
3. **Type Safety:** Good use of TypeScript types and Java records
4. **Transaction Tracking:** All operations create proper transaction records
5. **Authorization:** Good role-based checks (PARENT can give, CHILD can receive)
6. **User Experience:** Thoughtful UI with progress bars, notifications, etc.
7. **Code Organization:** Clear file structure and naming conventions

---

## 📋 Recommendations Summary

### Must Fix Before Production:
1. ✅ Add optimistic locking (`@Version`) to `ChildWalletEntity`
2. ✅ Add ownership validation in `allocateToSavingsGoals()`
3. ✅ Add over-allocation validation in `allocateToSavingsGoals()`
4. ✅ Add explicit transaction rollback strategy
5. ✅ Add input validation with Bean Validation annotations

### Should Fix Soon:
6. ✅ Remove duplicate validation between controller and service
7. ✅ Add structured logging for all financial operations
8. ✅ Fix potential N+1 queries with eager fetching
9. ✅ Add error boundaries in React components
10. ✅ Add max limits for amounts and string lengths

### Nice to Have:
11. ✅ Add unit tests for service methods
12. ✅ Add integration tests for API endpoints
13. ✅ Add monitoring/alerting for balance anomalies
14. ✅ Consider adding a "transaction log" view for auditing
15. ✅ Add rate limiting to prevent abuse

---

## 🔍 Code Quality Observations

### Backend:
- **Good:** Use of `@Transactional` for data consistency
- **Good:** Clear method names and documentation
- **Needs Work:** Error messages are user-facing (good) but could be more structured
- **Needs Work:** Some methods are quite long (e.g., `addAllowance` is 100+ lines)

### Frontend:
- **Good:** Reusable components and clear prop types
- **Good:** Loading states and error handling in most places
- **Needs Work:** Some components could be split into smaller pieces
- **Needs Work:** API client could use a more robust error handling pattern

---

## 🎓 Learning Opportunities

1. **Concurrency:** Study optimistic vs pessimistic locking strategies
2. **Transactions:** Deep dive into Spring `@Transactional` behavior
3. **Security:** Review OWASP Top 10, especially around financial data
4. **Testing:** Add comprehensive test coverage, especially for edge cases

---

## 📝 Final Notes

This is a **solid implementation** that demonstrates good engineering practices. The main concerns are around **concurrency** and **data consistency**, which are critical for financial systems. With the recommended fixes, this codebase will be production-ready.

**Estimated Effort to Address Critical Issues:** 2-3 days  
**Estimated Effort for All Recommendations:** 1-2 weeks

---

**Review Status:** ⚠️ **APPROVED WITH CONDITIONS**  
Address critical issues (#1-3) before production deployment.
