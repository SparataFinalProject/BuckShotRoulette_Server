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

    /** 싱글 엔딩(결과창)을 볼 때 주는 경험치. */
    public static int singleClearExp() {
        return STREAK_WIN_EXP;
    }

    /** 3~4인 라운드에서 이긴 사람에게 주는 경험치. */
    public static int roundWinExp() {
        return STREAK_WIN_EXP;
    }

    /** 1대1 한 판의 경험치. 연승 모드와 같이 5턴 미만이면 없고, 이기면 20, 지면 5 (연승 보너스는 없다). */
    public static int matchExp(boolean won, int turns) {
        if (turns < MIN_TURNS) {
            return 0;
        }
        return won ? STREAK_WIN_EXP : STREAK_LOSE_EXP;
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

