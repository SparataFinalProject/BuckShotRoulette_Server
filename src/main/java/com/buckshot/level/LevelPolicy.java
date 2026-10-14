package com.buckshot.level;

import com.buckshot.streak.GameResult;

public final class LevelPolicy {

    private LevelPolicy() {
    }

    static final int STREAK_WIN_EXP = 20;
    static final int STREAK_BONUS_PER_WIN = 2;
    static final int STREAK_BONUS_MAX = 20;
    static final int STREAK_LOSE_EXP = 5;
    static final int MIN_TURNS = 5;
    static final int EXP_PER_LEVEL = 100;

    public static int levelOf(int exp) {
        int level = 1;
        int remaining = exp;
        while (remaining >= EXP_PER_LEVEL * level) {
            remaining -= EXP_PER_LEVEL * level;
            level++;
        }
        return level;
    }

    public static int streakExp(GameResult result, int streak, int turns) {
        if (turns < MIN_TURNS) {
            return 0;
        }
        return switch (result) {
            case LOSE -> STREAK_LOSE_EXP;
            case WIN -> STREAK_WIN_EXP + Math.min(streak * STREAK_BONUS_PER_WIN, STREAK_BONUS_MAX);
        };
    }
}

