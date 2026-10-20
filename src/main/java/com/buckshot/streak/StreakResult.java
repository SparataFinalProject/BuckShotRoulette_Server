package com.buckshot.streak;

/**
 * 연승 모드 한 판의 결과.
 *
 * @param streak      결과 반영 후 현재 연승 (지면 0)
 * @param bestStreak  결과 반영 후 최고 연승
 * @param isNewBest   이번 판으로 최고 기록을 갱신했는지
 * @param expGained   이번 판에서 얻은 경험치
 * @param moneyGained 이번 판에서 얻은 돈 (StreakRewards.cash)
 */
public record StreakResult(int streak, int bestStreak, boolean isNewBest, int expGained, int moneyGained) {

    public StreakResult(int streak, int bestStreak, boolean isNewBest, int expGained) {
        this(streak, bestStreak, isNewBest, expGained, 0);
    }
}
