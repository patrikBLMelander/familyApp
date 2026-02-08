package com.familyapp.domain.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseCategory(
        UUID id,
        String name,
        String emoji,
        boolean isDefault,
        OffsetDateTime createdAt
) {
}
