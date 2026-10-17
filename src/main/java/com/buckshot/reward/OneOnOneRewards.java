package com.buckshot.reward;

import com.buckshot.level.LevelPolicy;
import lombok.extern.slf4j.Slf4j;

/** 1대1 판(바로 매칭·랭크 매칭)이 끝날 때의 보상: 승자는 돈과 경험치, 패자는 경험치. */
@Slf4j
public final class OneOnOneRewards {

    /** 승자에게 주는 돈 (원작 첫 라운드 상금과 같은 값). */
    public static final int WIN_CASH = 70_000;

    private OneOnOneRewards() {
    }

    /**
     * @param turns       이 판에서 지나간 차례 수 (5턴 미만이면 경험치 없음)
     * @param loserQuit   진 사람이 중간에 나간 것인가 (나간 사람은 경험치 없음)
     */
    public static void grant(RewardService rewards, long winnerId, long loserId, int turns, boolean loserQuit) {
        if (rewards == null) return;
        try {
            rewards.reward(winnerId, WIN_CASH, LevelPolicy.matchExp(true, turns));
            if (!loserQuit) rewards.reward(loserId, 0, LevelPolicy.matchExp(false, turns));
        } catch (RuntimeException e) {
            log.warn("[reward] 1대1 보상 실패 winner={} loser={}: {}", winnerId, loserId, e.getMessage(), e);
        }
    }
}
