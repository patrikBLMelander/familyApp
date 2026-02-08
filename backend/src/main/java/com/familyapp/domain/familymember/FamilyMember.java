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
        Boolean menstrualCycleEnabled,
        Boolean menstrualCyclePrivate,
        Boolean petEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public enum Role {
        CHILD,      // Yngre barn - enkel vy, bara tasks
        ASSISTANT,  // Äldre barn - kan skapa events, få djur, men inte hantera familj
        PARENT      // Vuxna - full access
    }
}

