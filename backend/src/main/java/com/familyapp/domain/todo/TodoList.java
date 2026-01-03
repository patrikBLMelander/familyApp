package com.familyapp.domain.todo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TodoList(
        UUID id,
        String name,
        int position,
        String color,
        boolean isPrivate,
        UUID ownerId,
        UUID familyId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<TodoItem> items
) {
}


