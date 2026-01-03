package com.familyapp.domain.familymember;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FamilyMember(
        UUID id,
        String name,
        String deviceToken,
        String email,
        Role role,
        UUID familyId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public enum Role {
        CHILD,
        PARENT
    }
}

