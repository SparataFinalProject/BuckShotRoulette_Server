package com.buckshot.ranking.dto;

import com.buckshot.streak.entity.StreakRun;
import com.buckshot.user.entity.User;
import java.time.LocalDateTime;

/**
 * 연승 탭에서만 채워지는 칸 (최고 기록을 세운 도전 기준). 비율은 저장하지 않고 여기서 계산한다.
 *
 * @param turns       최고 기록의 턴 합계 (이긴 판만)
 * @param turnsPerWin 한 판 이기는 데 평균 턴
 * @param accuracy    맞는 판단 비율 (%, 0~100)
 * @param runId       VIEW(세부정보)를 열 때 쓰는 도전 id
 */
public record StreakStats(int turns, double turnsPerWin, double accuracy, int damageTaken,
                          LocalDateTime achievedAt, Long runId) {

    /** run이 없으면(데이터가 어긋난 경우) 비율 칸은 0으로 둔다. 리더보드 전체가 깨지지 않게. */
    public static StreakStats of(User user, StreakRun run) {
        if (run == null) {
            return new StreakStats(user.getBestStreakTurns(), 0, 0, 0, user.getBestAchievedAt(), null);
        }
        return new StreakStats(
                user.getBestStreakTurns(),
                ratio(run.getTurns(), run.getWins()),
                ratio(run.getCorrectShots() * 100, run.getShots()),
                run.getDamageTaken(),
                user.getBestAchievedAt(),
                run.getId());
    }

    /** 0으로 나누지 않는다 (아직 쏜 적이 없으면 0). */
    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
