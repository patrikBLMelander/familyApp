package com.familyapp.domain.todo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TodoItem(
        UUID id,
        String description,
        boolean done,
        int position,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}



