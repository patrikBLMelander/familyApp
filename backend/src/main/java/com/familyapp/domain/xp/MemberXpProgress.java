package com.familyapp.domain.xp;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberXpProgress(
        UUID id,
        UUID memberId,
        int year,
        int month, // 1-12
        int currentXp,
        int currentLevel, // 1-10
        int totalTasksCompleted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static final int MAX_LEVEL = 10;
    public static final int XP_PER_LEVEL = 100;

    /**
     * Calculate level based on XP (1-10)
     * Level 1: 0-99 XP
     * Level 2: 100-199 XP
     * ...
     * Level 10: 900-999 XP
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
     * Get XP progress within current level (0-99)
     */
    public int getXpInCurrentLevel() {
        return currentXp % XP_PER_LEVEL;
    }
}

