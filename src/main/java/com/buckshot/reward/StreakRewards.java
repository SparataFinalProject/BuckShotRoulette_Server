package com.buckshot.reward;

import com.buckshot.streak.GameResult;

/** 연승 모드(딜러전) 한 판의 상금. 경험치는 LevelPolicy.streakExp. */
public final class StreakRewards {

    /** 이긴 판의 기본 상금 (1대1 승리와 같다). */
    public static final int WIN_CASH = OneOnOneRewards.WIN_CASH;
    /** 연승이 이어질 때 1승마다 더하는 상금 (2연승째부터). */
    public static final int BONUS_PER_WIN = 10_000;
    /** 연승 보너스 상한. */
    public static final int BONUS_MAX = 100_000;

    private StreakRewards() {
    }

    /**
     * 상대가 서버 딜러라 짜고 일찍 끝내는 일이 없으므로, 1대1과 달리 턴 수 제한 없이 이기면 준다.
     * (연승 모드의 turns는 사람 차례만 세서, 빨리 이긴 판은 5턴이 안 되는 일이 흔하다)
     *
     * @param streak 이번 판까지 반영한 연승 (이긴 판이면 1 이상)
     * @param turns  이 판의 사람 차례 수 (지금은 쓰지 않는다)
     * @return 1연승 70,000, 2연승 80,000, ... 11연승부터 170,000. 지면 0
     */
    public static int cash(GameResult result, int streak, int turns) {
        if (result != GameResult.WIN) return 0;
        return WIN_CASH + Math.min(Math.max(0, streak - 1) * BONUS_PER_WIN, BONUS_MAX);
    }
}
