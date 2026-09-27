package com.familyapp.domain.family;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Family(
        UUID id,
        String name,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String currency // ISO 4217, e.g. SEK
) {
}

