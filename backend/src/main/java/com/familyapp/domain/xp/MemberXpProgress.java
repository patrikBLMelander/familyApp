package com.familyapp.domain.xp;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberXpProgress(
        UUID id,
        UUID memberId,
        int year,
        int month, // 1-12
        int currentXp,
        int currentLevel, // 1-5
        int totalTasksCompleted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static final int MAX_LEVEL = 5;
    public static final int XP_PER_LEVEL = 24;

    /**
     * Calculate level based on XP (1-5)
     * Level 1: 0-23 XP
     * Level 2: 24-47 XP
     * Level 3: 48-71 XP
     * Level 4: 72-95 XP
     * Level 5: 96-119 XP
     */
    public static int calculateLevel(int xp) {
        int level = (xp / XP_PER_LEVEL) + 1;
        return Math.min(level, MAX_LEVEL);
    }

    /**
     * Get XP needed for next level
     */
    public int getXpForNextLevel() {
        if (currentLevel >= MAX_LEVEL) {
            return 0; // Already at max level
        }
        return (currentLevel * XP_PER_LEVEL) - currentXp;
    }

    /**
     * Get XP progress within current level (0-23)
     */
    public int getXpInCurrentLevel() {
        return currentXp % XP_PER_LEVEL;
    }
}

