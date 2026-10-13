package com.buckshot.ranking.dto;

import com.buckshot.streak.timeline.Timeline;
import java.util.List;

/** 다른 패키지 테스트에서 StreakGameDetail.rounds(계산)를 부르기 위한 통로 (테스트 전용). */
public final class StreakGameDetailTestAccess {

    private StreakGameDetailTestAccess() {
    }

    public static List<StreakGameDetail.RoundView> rounds(Timeline timeline) {
        return StreakGameDetail.rounds(timeline);
    }
}
