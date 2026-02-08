package com.familyapp.domain.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletNotification(
        UUID id,
        UUID memberId,
        UUID transactionId,
        int amount,
        String description,
        OffsetDateTime shownAt,
        OffsetDateTime createdAt
) {
    public boolean isShown() {
        return shownAt != null;
    }
}
