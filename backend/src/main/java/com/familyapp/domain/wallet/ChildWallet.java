package com.familyapp.domain.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChildWallet(
        UUID id,
        UUID memberId,
        int balance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
