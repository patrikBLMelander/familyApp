package com.familyapp.api.wallet;

import com.familyapp.application.i18n.MessageTranslator;
import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.application.subscription.EntitlementGuard;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.wallet.WalletService;
import com.familyapp.domain.wallet.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;
    private final FamilyMemberService memberService;
    private final EntitlementGuard entitlementGuard;
    private final MessageTranslator translator;

    public WalletController(
            WalletService walletService,
            FamilyMemberService memberService,
            EntitlementGuard entitlementGuard,
            MessageTranslator translator
    ) {
        this.walletService = walletService;
        this.memberService = memberService;
        this.entitlementGuard = entitlementGuard;
        this.translator = translator;
    }

    /**
     * Get wallet balance for current member (from device token)
     */
    @GetMapping("/balance")
    public WalletBalanceResponse getBalance(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        var wallet = walletService.getBalance(memberId);
        return new WalletBalanceResponse(wallet.id(), wallet.memberId(), wallet.balance(),
                walletService.currencyOf(memberId));
    }

    /**
     * Add allowance (give money) to a child
     */
    @PostMapping("/allowance")
    public ResponseEntity<Void> addAllowance(
            @RequestBody AddAllowanceRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        entitlementGuard.requireEntitled(deviceToken);
        UUID giverMemberId = getMemberIdFromToken(deviceToken);
        
        List<WalletService.SavingsGoalAllocation> allocations = null;
        if (request.savingsGoalAllocations() != null && !request.savingsGoalAllocations().isEmpty()) {
            allocations = request.savingsGoalAllocations().stream()
                    .map(a -> new WalletService.SavingsGoalAllocation(a.savingsGoalId(), a.amount()))
                    .collect(Collectors.toList());
        }
        
        walletService.addAllowance(
                request.childMemberId(),
                request.amount(),
                request.description(),
                giverMemberId,
                allocations
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Allocate money from wallet balance to savings goals
     */
    @PostMapping("/allocate-to-goals")
    public ResponseEntity<Void> allocateToSavingsGoals(
            @RequestBody AllocateToGoalsRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        
        if (request.savingsGoalAllocations() == null || request.savingsGoalAllocations().isEmpty()) {
            throw new LocalizedException("wallet.allocation.atLeastOneGoal");
        }
        
        List<WalletService.SavingsGoalAllocation> allocations = request.savingsGoalAllocations().stream()
                .map(a -> new WalletService.SavingsGoalAllocation(a.savingsGoalId(), a.amount()))
                .collect(Collectors.toList());
        
        walletService.allocateToSavingsGoals(memberId, allocations);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Record expense (purchase)
     */
    @PostMapping("/expense")
    public ResponseEntity<Void> recordExpense(
            @RequestBody RecordExpenseRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        
        List<WalletService.SavingsGoalAllocation> allocations = null;
        if (request.savingsGoalAllocations() != null && !request.savingsGoalAllocations().isEmpty()) {
            allocations = request.savingsGoalAllocations().stream()
                    .map(a -> new WalletService.SavingsGoalAllocation(a.savingsGoalId(), a.amount()))
                    .collect(Collectors.toList());
        }

        walletService.recordExpense(
                memberId,
                request.amount(),
                request.description(),
                request.categoryId(),
                allocations
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Get transaction history
     */
    @GetMapping("/transactions")
    public List<WalletTransactionResponse> getTransactions(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        return walletService.getTransactionHistory(memberId, limit).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get unshown notifications
     */
    @GetMapping("/notifications/unshown")
    public List<WalletNotificationResponse> getUnshownNotifications(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        return walletService.getUnshownNotifications(memberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Mark notification as shown
     */
    @PostMapping("/notifications/{notificationId}/mark-shown")
    public ResponseEntity<Void> markNotificationAsShown(
            @PathVariable("notificationId") UUID notificationId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Took no token at all, so anyone could dismiss anyone's notifications.
        requireWalletAccess(deviceToken, walletService.getNotificationOwnerId(notificationId));
        walletService.markNotificationAsShown(notificationId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get expense categories
     */
    @GetMapping("/categories")
    public List<ExpenseCategoryResponse> getCategories() {
        return walletService.getExpenseCategories().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get savings goals for current member
     */
    @GetMapping("/savings-goals")
    public List<SavingsGoalResponse> getSavingsGoals(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        return walletService.getSavingsGoals(memberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get active savings goals for current member
     */
    @GetMapping("/savings-goals/active")
    public List<SavingsGoalResponse> getActiveSavingsGoals(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        return walletService.getActiveSavingsGoals(memberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create savings goal
     */
    @PostMapping("/savings-goals")
    public ResponseEntity<SavingsGoalResponse> createSavingsGoal(
            @RequestBody CreateSavingsGoalRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        var goal = walletService.createSavingsGoal(
                memberId,
                request.name(),
                request.targetAmount(),
                request.emoji()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(goal));
    }

    /**
     * Delete savings goal
     */
    @DeleteMapping("/savings-goals/{goalId}")
    public ResponseEntity<Void> deleteSavingsGoal(
            @PathVariable("goalId") UUID goalId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        walletService.deleteSavingsGoal(memberId, goalId);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark savings goal as purchased
     */
    @PostMapping("/savings-goals/{goalId}/mark-purchased")
    public ResponseEntity<Void> markGoalAsPurchased(
            @PathVariable("goalId") UUID goalId,
            @RequestBody MarkGoalPurchasedRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID memberId = getMemberIdFromToken(deviceToken);
        walletService.markGoalAsPurchased(memberId, goalId, request.purchaseTransactionId());
        return ResponseEntity.ok().build();
    }

    /**
     * Get wallet balance for a specific member (for parents to see children's wallets)
     */
    @GetMapping("/members/{memberId}/balance")
    public WalletBalanceResponse getMemberBalance(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireWalletAccess(deviceToken, memberId);

        var wallet = walletService.getBalance(memberId);
        return new WalletBalanceResponse(wallet.id(), wallet.memberId(), wallet.balance(),
                walletService.currencyOf(memberId));
    }

    /**
     * Get active savings goals for a specific member
     */
    @GetMapping("/members/{memberId}/savings-goals/active")
    public List<SavingsGoalResponse> getMemberActiveSavingsGoals(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireWalletAccess(deviceToken, memberId);

        return walletService.getActiveSavingsGoals(memberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get transaction history for a specific member
     */
    @GetMapping("/members/{memberId}/transactions")
    public List<WalletTransactionResponse> getMemberTransactions(
            @PathVariable("memberId") UUID memberId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireWalletAccess(deviceToken, memberId);

        return walletService.getTransactionHistory(memberId, limit).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Record expense for a specific member (for parents to register purchases on behalf of children)
     */
    @PostMapping("/members/{memberId}/expense")
    public ResponseEntity<Void> recordExpenseForMember(
            @PathVariable("memberId") UUID memberId,
            @RequestBody RecordExpenseRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireWalletAccess(deviceToken, memberId);
        entitlementGuard.requireEntitled(deviceToken);

        List<WalletService.SavingsGoalAllocation> allocations = null;
        if (request.savingsGoalAllocations() != null && !request.savingsGoalAllocations().isEmpty()) {
            allocations = request.savingsGoalAllocations().stream()
                    .map(a -> new WalletService.SavingsGoalAllocation(a.savingsGoalId(), a.amount()))
                    .collect(Collectors.toList());
        }

        walletService.recordExpense(
                memberId,
                request.amount(),
                request.description(),
                request.categoryId(),
                allocations
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Helper methods
    /**
     * Asserts the caller may see or touch this member's wallet: the member themselves,
     * or a parent of the same family.
     *
     * These endpoints used to guard with `if (deviceToken != null ...)` and no else, so
     * an unauthenticated caller skipped the check and could read a child's balance and
     * full transaction history, or record an expense against their wallet.
     */
    private void requireWalletAccess(String deviceToken, UUID memberId) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new LocalizedException("auth.notSignedIn");
        }
        var requester = memberService.getMemberByDeviceToken(deviceToken);
        var member = memberService.getMemberById(memberId);
        if (requester.familyId() == null || !requester.familyId().equals(member.familyId())) {
            throw new LocalizedException("error.accessDenied");
        }
        boolean isSelf = requester.id().equals(memberId);
        boolean isParent = requester.role() == com.familyapp.domain.familymember.FamilyMember.Role.PARENT;
        if (!isSelf && !isParent) {
            throw new LocalizedException("error.accessDenied");
        }
    }

    private UUID getMemberIdFromToken(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new LocalizedException("auth.notSignedIn");
        }
        try {
            var member = memberService.getMemberByDeviceToken(deviceToken);
            return member.id();
        } catch (IllegalArgumentException e) {
            throw new LocalizedException("auth.notSignedIn");
        }
    }

    // Response mapping methods
    private WalletTransactionResponse toResponse(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.id(),
                transaction.walletId(),
                transaction.amount(),
                transaction.transactionType().name(),
                translator.systemText(transaction.description()),
                transaction.categoryId(),
                transaction.createdByMemberId(),
                transaction.isDeleted(),
                transaction.deletedAt(),
                transaction.deletedByMemberId(),
                transaction.createdAt()
        );
    }

    private WalletNotificationResponse toResponse(WalletNotification notification) {
        return new WalletNotificationResponse(
                notification.id(),
                notification.memberId(),
                notification.transactionId(),
                notification.amount(),
                translator.systemText(notification.description()),
                notification.shownAt(),
                notification.createdAt()
        );
    }

    private ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(
                category.id(),
                category.isDefault() ? translator.systemText(category.name()) : category.name(),
                category.emoji(),
                category.isDefault()
        );
    }

    private SavingsGoalResponse toResponse(SavingsGoal goal) {
        return new SavingsGoalResponse(
                goal.id(),
                goal.memberId(),
                goal.name(),
                goal.targetAmount(),
                goal.currentAmount(),
                goal.emoji(),
                goal.isActive(),
                goal.isCompleted(),
                goal.isPurchased(),
                goal.completedAt(),
                goal.purchasedAt(),
                goal.purchaseTransactionId(),
                goal.getProgressPercentage(),
                goal.getRemainingAmount(),
                goal.createdAt(),
                goal.updatedAt()
        );
    }

    // Request/Response DTOs
    public record WalletBalanceResponse(UUID id, UUID memberId, int balance, String currency) {
    }

    public record AddAllowanceRequest(
            UUID childMemberId,
            int amount,
            String description,
            List<SavingsGoalAllocationRequest> savingsGoalAllocations
    ) {
    }

    public record AllocateToGoalsRequest(
            List<SavingsGoalAllocationRequest> savingsGoalAllocations
    ) {
    }

    public record RecordExpenseRequest(
            int amount,
            String description,
            UUID categoryId,
            List<SavingsGoalAllocationRequest> savingsGoalAllocations
    ) {
    }

    public record SavingsGoalAllocationRequest(UUID savingsGoalId, int amount) {
    }

    public record WalletTransactionResponse(
            UUID id,
            UUID walletId,
            int amount,
            String transactionType,
            String description,
            UUID categoryId,
            UUID createdByMemberId,
            boolean isDeleted,
            java.time.OffsetDateTime deletedAt,
            UUID deletedByMemberId,
            java.time.OffsetDateTime createdAt
    ) {
    }

    public record WalletNotificationResponse(
            UUID id,
            UUID memberId,
            UUID transactionId,
            int amount,
            String description,
            java.time.OffsetDateTime shownAt,
            java.time.OffsetDateTime createdAt
    ) {
    }

    public record ExpenseCategoryResponse(UUID id, String name, String emoji, boolean isDefault) {
    }

    public record SavingsGoalResponse(
            UUID id,
            UUID memberId,
            String name,
            int targetAmount,
            int currentAmount,
            String emoji,
            boolean isActive,
            boolean isCompleted,
            boolean isPurchased,
            java.time.OffsetDateTime completedAt,
            java.time.OffsetDateTime purchasedAt,
            UUID purchaseTransactionId,
            int progressPercentage,
            int remainingAmount,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt
    ) {
    }

    public record CreateSavingsGoalRequest(String name, int targetAmount, String emoji) {
    }

    public record MarkGoalPurchasedRequest(UUID purchaseTransactionId) {
    }
}
