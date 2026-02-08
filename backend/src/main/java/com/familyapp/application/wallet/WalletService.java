package com.familyapp.application.wallet;

import com.familyapp.domain.wallet.*;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.wallet.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class WalletService {

    private final ChildWalletJpaRepository walletRepository;
    private final WalletTransactionJpaRepository transactionRepository;
    private final WalletTransactionSavingsGoalJpaRepository transactionSavingsGoalRepository;
    private final SavingsGoalJpaRepository savingsGoalRepository;
    private final ExpenseCategoryJpaRepository categoryRepository;
    private final WalletNotificationJpaRepository notificationRepository;
    private final FamilyMemberJpaRepository memberRepository;

    public WalletService(
            ChildWalletJpaRepository walletRepository,
            WalletTransactionJpaRepository transactionRepository,
            WalletTransactionSavingsGoalJpaRepository transactionSavingsGoalRepository,
            SavingsGoalJpaRepository savingsGoalRepository,
            ExpenseCategoryJpaRepository categoryRepository,
            WalletNotificationJpaRepository notificationRepository,
            FamilyMemberJpaRepository memberRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.transactionSavingsGoalRepository = transactionSavingsGoalRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.categoryRepository = categoryRepository;
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Get or create wallet for a member
     */
    private ChildWalletEntity getOrCreateWallet(UUID memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    var member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
                    
                    var wallet = new ChildWalletEntity();
                    wallet.setId(UUID.randomUUID());
                    wallet.setMember(member);
                    wallet.setBalance(0);
                    wallet.setVersion(0L); // Initialize version for optimistic locking
                    wallet.setCreatedAt(OffsetDateTime.now());
                    wallet.setUpdatedAt(OffsetDateTime.now());
                    return walletRepository.save(wallet);
                });
    }

    /**
     * Get wallet balance for a member
     */
    @Transactional(readOnly = true)
    public ChildWallet getBalance(UUID memberId) {
        var walletEntity = getOrCreateWallet(memberId);
        return toDomain(walletEntity);
    }

    /**
     * Add allowance (give money) to a child
     */
    @Transactional(rollbackFor = Exception.class)
    public void addAllowance(
            UUID childMemberId,
            int amount,
            String description,
            UUID givenByMemberId,
            List<SavingsGoalAllocation> savingsGoalAllocations
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Beloppet måste vara större än 0");
        }

        var child = memberRepository.findById(childMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Barn hittades inte: " + childMemberId));

        // Only CHILD and ASSISTANT can receive allowance
        String role = child.getRole();
        if (!"CHILD".equals(role) && !"ASSISTANT".equals(role)) {
            throw new IllegalArgumentException("Endast barn kan få månads-/veckopeng");
        }

        var giver = memberRepository.findById(givenByMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Givare hittades inte: " + givenByMemberId));

        // Only PARENT can give allowance
        if (!"PARENT".equals(giver.getRole())) {
            throw new IllegalArgumentException("Endast vuxna kan ge månads-/veckopeng");
        }

        // Validate savings goal allocations BEFORE making any changes
        if (savingsGoalAllocations != null && !savingsGoalAllocations.isEmpty()) {
            int totalAllocated = savingsGoalAllocations.stream()
                    .mapToInt(SavingsGoalAllocation::amount)
                    .sum();
            if (totalAllocated > amount) {
                throw new IllegalArgumentException("Summan av sparmål kan inte överstiga beloppet");
            }
            
            // Validate all goals exist and belong to child (transaction atomicity)
            for (var allocation : savingsGoalAllocations) {
                if (allocation.amount() <= 0) {
                    throw new IllegalArgumentException("Allokering måste vara större än 0");
                }
                
                var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                        .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + allocation.savingsGoalId()));
                
                if (!goalEntity.getMember().getId().equals(childMemberId)) {
                    throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
                }
                
                // Validate over-allocation
                int remaining = goalEntity.getTargetAmount() - goalEntity.getCurrentAmount();
                if (allocation.amount() > remaining) {
                    throw new IllegalArgumentException(
                            String.format("Mål '%s' skulle överskridas. Max %d kr kvar.",
                                    goalEntity.getName(), remaining)
                    );
                }
            }
        }

        var wallet = getOrCreateWallet(childMemberId);
        
        // Create transaction
        var transaction = new WalletTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setTransactionType(WalletTransaction.TransactionType.ALLOWANCE.name());
        transaction.setDescription(description);
        transaction.setCreatedByMember(giver);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setDeleted(false);
        transaction = transactionRepository.save(transaction);

        // Update wallet balance (optimistic locking will prevent race conditions)
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);

        // Create savings goal allocations and update goals (all validated, so safe to update)
        if (savingsGoalAllocations != null && !savingsGoalAllocations.isEmpty()) {
            for (var allocation : savingsGoalAllocations) {
                var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                        .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + allocation.savingsGoalId()));

                // Create junction table entry
                var junctionId = new WalletTransactionSavingsGoalId();
                junctionId.setTransaction(transaction.getId());
                junctionId.setSavingsGoal(goalEntity.getId());
                
                var junction = new WalletTransactionSavingsGoalEntity();
                junction.setId(junctionId);
                junction.setTransaction(transaction);
                junction.setSavingsGoal(goalEntity);
                junction.setAmount(allocation.amount());
                transactionSavingsGoalRepository.save(junction);

                // Update goal
                goalEntity.setCurrentAmount(goalEntity.getCurrentAmount() + allocation.amount());
                
                // Check if goal is completed
                if (goalEntity.getCurrentAmount() >= goalEntity.getTargetAmount() && !goalEntity.isCompleted()) {
                    goalEntity.setCompleted(true);
                    goalEntity.setCompletedAt(OffsetDateTime.now());
                }
                
                goalEntity.setUpdatedAt(OffsetDateTime.now());
                savingsGoalRepository.save(goalEntity);
            }
        }

        // Create notification
        var notification = new WalletNotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setMember(child);
        notification.setTransaction(transaction);
        notification.setAmount(amount);
        notification.setDescription(description);
        notification.setShownAt(null); // Not shown yet
        notification.setCreatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    /**
     * Record expense (purchase) for a member
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordExpense(
            UUID memberId,
            int amount,
            String description,
            UUID categoryId,
            List<SavingsGoalAllocation> savingsGoalAllocations
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Beloppet måste vara större än 0");
        }

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Familjemedlem hittades inte: " + memberId));

        // Only CHILD and ASSISTANT can record expenses
        String role = member.getRole();
        if (!"CHILD".equals(role) && !"ASSISTANT".equals(role)) {
            throw new IllegalArgumentException("Endast barn kan registrera köp");
        }

        var wallet = getOrCreateWallet(memberId);

        // Validate balance
        if (wallet.getBalance() < amount) {
            throw new IllegalArgumentException(
                    String.format("Du har inte tillräckligt med pengar. Du har %d kr.", wallet.getBalance())
            );
        }

        // Validate category
        ExpenseCategoryEntity category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Kategori hittades inte: " + categoryId));
        }

        // Validate savings goal allocations
        if (savingsGoalAllocations != null && !savingsGoalAllocations.isEmpty()) {
            int totalAllocated = savingsGoalAllocations.stream()
                    .mapToInt(SavingsGoalAllocation::amount)
                    .sum();

            if (totalAllocated > amount) {
                throw new IllegalArgumentException(
                        "Summan av sparmål kan inte överstiga köpbeloppet"
                );
            }

            // Validate each goal
            for (var allocation : savingsGoalAllocations) {
                var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                        .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + allocation.savingsGoalId()));

                if (!goalEntity.getMember().getId().equals(memberId)) {
                    throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
                }

                if (goalEntity.getCurrentAmount() + allocation.amount() > goalEntity.getTargetAmount()) {
                    throw new IllegalArgumentException(
                            String.format("Mål '%s' skulle överskridas. Max %d kr kvar.",
                                    goalEntity.getName(),
                                    goalEntity.getTargetAmount() - goalEntity.getCurrentAmount())
                    );
                }
            }
        }

        // Create transaction
        var transaction = new WalletTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setWallet(wallet);
        transaction.setAmount(-amount); // Negative for expense
        transaction.setTransactionType(WalletTransaction.TransactionType.EXPENSE.name());
        transaction.setDescription(description);
        transaction.setCategory(category);
        transaction.setCreatedByMember(member);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setDeleted(false);
        transaction = transactionRepository.save(transaction);

        // Update wallet balance
        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);

        // Create savings goal allocations and update goals
        if (savingsGoalAllocations != null && !savingsGoalAllocations.isEmpty()) {
            for (var allocation : savingsGoalAllocations) {
                var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                        .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + allocation.savingsGoalId()));

                // Create junction table entry
                var junctionId = new WalletTransactionSavingsGoalId();
                junctionId.setTransaction(transaction.getId());
                junctionId.setSavingsGoal(goalEntity.getId());
                
                var junction = new WalletTransactionSavingsGoalEntity();
                junction.setId(junctionId);
                junction.setTransaction(transaction); // Set for JPA to resolve the relationship
                junction.setSavingsGoal(goalEntity); // Set for JPA to resolve the relationship
                junction.setAmount(allocation.amount());
                transactionSavingsGoalRepository.save(junction);

                // Update goal
                goalEntity.setCurrentAmount(goalEntity.getCurrentAmount() + allocation.amount());
                
                // Check if goal is completed
                if (goalEntity.getCurrentAmount() >= goalEntity.getTargetAmount() && !goalEntity.isCompleted()) {
                    goalEntity.setCompleted(true);
                    goalEntity.setCompletedAt(OffsetDateTime.now());
                }
                
                goalEntity.setUpdatedAt(OffsetDateTime.now());
                savingsGoalRepository.save(goalEntity);
            }
        }
    }

    /**
     * Allocate money from wallet balance to savings goals
     * This allows children to move money from their account to their savings goals
     */
    @Transactional(rollbackFor = Exception.class)
    public void allocateToSavingsGoals(UUID memberId, List<SavingsGoalAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new IllegalArgumentException("Minst ett sparmål måste anges");
        }

        var wallet = getOrCreateWallet(memberId);
        int totalAmount = allocations.stream()
                .mapToInt(SavingsGoalAllocation::amount)
                .sum();

        if (totalAmount <= 0) {
            throw new IllegalArgumentException("Totalt belopp måste vara större än 0");
        }

        if (wallet.getBalance() < totalAmount) {
            throw new IllegalArgumentException("Otillräckligt saldo. Du har " + wallet.getBalance() + " kr men försöker fördela " + totalAmount + " kr");
        }

        // Validate all goals BEFORE making any changes (transaction atomicity)
        var validatedGoals = new java.util.ArrayList<SavingsGoalEntity>();
        for (var allocation : allocations) {
            if (allocation.amount() <= 0) {
                throw new IllegalArgumentException("Allokering måste vara större än 0");
            }
            
            var goalEntity = savingsGoalRepository.findById(allocation.savingsGoalId())
                    .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + allocation.savingsGoalId()));

            // CRITICAL FIX #2: Validate ownership
            if (!goalEntity.getMember().getId().equals(memberId)) {
                throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
            }

            // CRITICAL FIX #3: Validate over-allocation
            int remaining = goalEntity.getTargetAmount() - goalEntity.getCurrentAmount();
            if (allocation.amount() > remaining) {
                throw new IllegalArgumentException(
                        String.format("Mål '%s' skulle överskridas. Max %d kr kvar.",
                                goalEntity.getName(), remaining)
                );
            }

            validatedGoals.add(goalEntity);
        }

        // Create transaction to track the allocation
        var transaction = new WalletTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setWallet(wallet);
        transaction.setAmount(-totalAmount); // Negative because money leaves the account
        transaction.setTransactionType(WalletTransaction.TransactionType.SAVINGS_ALLOCATION.name());
        transaction.setDescription("Fördelning till sparmål");
        transaction.setCreatedByMember(memberRepository.findById(memberId).orElseThrow());
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setDeleted(false);
        transaction = transactionRepository.save(transaction);

        // Update wallet balance (optimistic locking will prevent race conditions)
        wallet.setBalance(wallet.getBalance() - totalAmount);
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);

        // Create savings goal allocations and update goals (all validated, so safe to update)
        for (int i = 0; i < allocations.size(); i++) {
            var allocation = allocations.get(i);
            var goalEntity = validatedGoals.get(i);

            // Create junction table entry
            var junctionId = new WalletTransactionSavingsGoalId();
            junctionId.setTransaction(transaction.getId());
            junctionId.setSavingsGoal(goalEntity.getId());
            
            var junction = new WalletTransactionSavingsGoalEntity();
            junction.setId(junctionId);
            junction.setTransaction(transaction);
            junction.setSavingsGoal(goalEntity);
            junction.setAmount(allocation.amount());
            transactionSavingsGoalRepository.save(junction);

            // Update goal
            goalEntity.setCurrentAmount(goalEntity.getCurrentAmount() + allocation.amount());
            
            // Check if goal is completed
            if (goalEntity.getCurrentAmount() >= goalEntity.getTargetAmount() && !goalEntity.isCompleted()) {
                goalEntity.setCompleted(true);
                goalEntity.setCompletedAt(OffsetDateTime.now());
            }
            
            goalEntity.setUpdatedAt(OffsetDateTime.now());
            savingsGoalRepository.save(goalEntity);
        }
    }

    /**
     * Get transaction history for a member
     */
    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactionHistory(UUID memberId, int limit) {
        var wallet = walletRepository.findByMemberId(memberId)
                .orElse(null);
        
        if (wallet == null) {
            return List.of();
        }

        var transactions = transactionRepository.findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(wallet.getId());
        
        if (limit > 0 && transactions.size() > limit) {
            transactions = transactions.subList(0, limit);
        }

        return transactions.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get unshown notifications for a member
     */
    @Transactional(readOnly = true)
    public List<WalletNotification> getUnshownNotifications(UUID memberId) {
        return notificationRepository.findByMemberIdAndShownAtIsNullOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Mark notification as shown
     */
    public void markNotificationAsShown(UUID notificationId) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notifikation hittades inte: " + notificationId));
        
        notification.setShownAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    /**
     * Get all expense categories
     */
    @Transactional(readOnly = true)
    public List<ExpenseCategory> getExpenseCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get savings goals for a member
     */
    @Transactional(readOnly = true)
    public List<SavingsGoal> getSavingsGoals(UUID memberId) {
        return savingsGoalRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get active savings goals for a member
     */
    @Transactional(readOnly = true)
    public List<SavingsGoal> getActiveSavingsGoals(UUID memberId) {
        return savingsGoalRepository.findByMemberIdAndIsActiveTrueAndIsCompletedFalseOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Create a new savings goal
     */
    public SavingsGoal createSavingsGoal(UUID memberId, String name, int targetAmount, String emoji) {
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("Målbeloppet måste vara större än 0");
        }

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Familjemedlem hittades inte: " + memberId));

        // Only CHILD and ASSISTANT can create savings goals
        String role = member.getRole();
        if (!"CHILD".equals(role) && !"ASSISTANT".equals(role)) {
            throw new IllegalArgumentException("Endast barn kan skapa sparmål");
        }

        var goal = new SavingsGoalEntity();
        goal.setId(UUID.randomUUID());
        goal.setMember(member);
        goal.setName(name);
        goal.setTargetAmount(targetAmount);
        goal.setCurrentAmount(0);
        goal.setEmoji(emoji);
        goal.setActive(true);
        goal.setCompleted(false);
        goal.setPurchased(false);
        goal.setCreatedAt(OffsetDateTime.now());
        goal.setUpdatedAt(OffsetDateTime.now());

        goal = savingsGoalRepository.save(goal);
        return toDomain(goal);
    }

    /**
     * Delete a savings goal
     */
    public void deleteSavingsGoal(UUID memberId, UUID goalId) {
        var goal = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + goalId));

        if (!goal.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
        }

        // Only allow deletion if goal is not completed or if completed but not purchased
        if (goal.isCompleted() && goal.isPurchased()) {
            throw new IllegalArgumentException("Kan inte ta bort ett köpt mål");
        }

        savingsGoalRepository.delete(goal);
    }

    /**
     * Mark a savings goal as purchased
     */
    public void markGoalAsPurchased(UUID memberId, UUID goalId, UUID purchaseTransactionId) {
        var goal = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Sparmål hittades inte: " + goalId));

        if (!goal.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("Sparmål tillhör inte detta barn");
        }

        if (!goal.isCompleted()) {
            throw new IllegalArgumentException("Målet är inte avklarat ännu");
        }

        goal.setPurchased(true);
        goal.setPurchasedAt(OffsetDateTime.now());
        goal.setPurchaseTransactionId(purchaseTransactionId);
        goal.setUpdatedAt(OffsetDateTime.now());

        savingsGoalRepository.save(goal);
    }

    // Domain conversion methods for categories and goals
    private ExpenseCategory toDomain(ExpenseCategoryEntity entity) {
        return new ExpenseCategory(
                entity.getId(),
                entity.getName(),
                entity.getEmoji(),
                entity.isDefault(),
                entity.getCreatedAt()
        );
    }

    private SavingsGoal toDomain(SavingsGoalEntity entity) {
        return new SavingsGoal(
                entity.getId(),
                entity.getMember().getId(),
                entity.getName(),
                entity.getTargetAmount(),
                entity.getCurrentAmount(),
                entity.getEmoji(),
                entity.isActive(),
                entity.isCompleted(),
                entity.isPurchased(),
                entity.getCompletedAt(),
                entity.getPurchasedAt(),
                entity.getPurchaseTransactionId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // Domain conversion methods
    private ChildWallet toDomain(ChildWalletEntity entity) {
        return new ChildWallet(
                entity.getId(),
                entity.getMember().getId(),
                entity.getBalance(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WalletTransaction toDomain(WalletTransactionEntity entity) {
        return new WalletTransaction(
                entity.getId(),
                entity.getWallet().getId(),
                entity.getAmount(),
                WalletTransaction.TransactionType.valueOf(entity.getTransactionType()),
                entity.getDescription(),
                entity.getCategory() != null ? entity.getCategory().getId() : null,
                entity.getCreatedByMember() != null ? entity.getCreatedByMember().getId() : null,
                entity.isDeleted(),
                entity.getDeletedAt(),
                entity.getDeletedByMember() != null ? entity.getDeletedByMember().getId() : null,
                entity.getCreatedAt()
        );
    }

    private WalletNotification toDomain(WalletNotificationEntity entity) {
        return new WalletNotification(
                entity.getId(),
                entity.getMember().getId(),
                entity.getTransaction().getId(),
                entity.getAmount(),
                entity.getDescription(),
                entity.getShownAt(),
                entity.getCreatedAt()
        );
    }

    /**
     * DTO for savings goal allocation
     */
    public record SavingsGoalAllocation(UUID savingsGoalId, int amount) {
    }
}
