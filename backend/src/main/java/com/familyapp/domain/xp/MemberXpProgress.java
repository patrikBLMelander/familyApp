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

    /** Total XP at which the pet reaches max level (level 5). */
    public static final int MAX_LEVEL_XP = 125;
    /** XP required per post-max "star" -- each star also grants one adventure ticket. */
    public static final int XP_PER_STAR = 50;
    /** How many stars the pet shows at most (the engine keeps granting tickets past this). */
    public static final int MAX_STARS = 5;

    // XP thresholds for each level
    // Level 1 → 2: 10 XP (0-9 XP = Level 1)
    // Level 2 → 3: 25 XP (10-34 XP = Level 2, total 35)
    // Level 3 → 4: 35 XP (35-69 XP = Level 3, total 70)
    // Level 4 → 5: 55 XP (70-124 XP = Level 4, total 125)
    // Level 5: 125+ XP
    private static final int[] XP_THRESHOLDS = {0, 10, 35, 70, 125};

    /**
     * Calculate level based on XP (1-5)
     * Level 1: 0-9 XP
     * Level 2: 10-34 XP
     * Level 3: 35-69 XP
     * Level 4: 70-124 XP
     * Level 5: 125+ XP
     */
    public static int calculateLevel(int xp) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (xp >= XP_THRESHOLDS[level - 1]) {
                return level;
            }
        }
        return 1; // Default to level 1
    }

    /**
     * Get XP needed for next level
     */
    public int getXpForNextLevel() {
        if (currentLevel >= MAX_LEVEL) {
            return 0; // Already at max level
        }
        int xpForNextLevel = XP_THRESHOLDS[currentLevel];
        int xpNeeded = xpForNextLevel - currentXp;
        return Math.max(0, xpNeeded);
    }

    /**
     * Get XP progress within current level
     */
    public int getXpInCurrentLevel() {
        if (currentLevel >= MAX_LEVEL) {
            return 0; // At max level, no progress
        }
        int xpForCurrentLevel = XP_THRESHOLDS[currentLevel - 1];
        return currentXp - xpForCurrentLevel;
    }

    /**
     * Number of {@link #XP_PER_STAR}-sized milestones crossed past max level.
     * Uncapped -- this is what drives the bonus adventure tickets so the most
     * active children never run dry, even after the fifth star.
     */
    public static int milestonesPastMax(int xp) {
        if (xp < MAX_LEVEL_XP) {
            return 0;
        }
        return (xp - MAX_LEVEL_XP) / XP_PER_STAR;
    }

    /** Stars shown on the pet (0-{@link #MAX_STARS}), earned after reaching max level. */
    public static int calculateStars(int xp) {
        return Math.min(MAX_STARS, milestonesPastMax(xp));
    }

    /** Stars shown on this month's pet (0-{@link #MAX_STARS}). */
    public int getStars() {
        return calculateStars(currentXp);
    }

    /**
     * XP left until the next star / bonus ticket. 0 until max level is reached;
     * after that it counts down within each {@link #XP_PER_STAR} band, and keeps
     * counting even past the fifth star (tickets continue, the star display caps).
     */
    public int getXpToNextStar() {
        if (currentXp < MAX_LEVEL_XP) {
            return 0;
        }
        int into = (currentXp - MAX_LEVEL_XP) % XP_PER_STAR;
        return XP_PER_STAR - into;
    }
}

