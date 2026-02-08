package com.familyapp.domain.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SavingsGoal(
        UUID id,
        UUID memberId,
        String name,
        int targetAmount,
        int currentAmount,
        String emoji,
        boolean isActive,
        boolean isCompleted,
        boolean isPurchased,
        OffsetDateTime completedAt,
        OffsetDateTime purchasedAt,
        UUID purchaseTransactionId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public int getProgressPercentage() {
        if (targetAmount == 0) return 0;
        return Math.min(100, (currentAmount * 100) / targetAmount);
    }

    public int getRemainingAmount() {
        return Math.max(0, targetAmount - currentAmount);
    }
}
