package com.familyapp.domain.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletTransaction(
        UUID id,
        UUID walletId,
        int amount,
        TransactionType transactionType,
        String description,
        UUID categoryId,
        UUID createdByMemberId,
        boolean isDeleted,
        OffsetDateTime deletedAt,
        UUID deletedByMemberId,
        OffsetDateTime createdAt
) {
    public enum TransactionType {
        ALLOWANCE,      // Månads-/veckopeng
        EXPENSE,        // Köp/utgift
        SAVINGS_ALLOCATION, // Fördelning från konto till sparmål (barn)
        MANUAL_ADJUSTMENT, // Manuell justering (vuxna)
        DELETION        // Borttagen transaktion
    }
}
