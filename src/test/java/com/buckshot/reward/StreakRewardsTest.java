package com.buckshot.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.buckshot.streak.GameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakRewardsTest {

    @Test
    @DisplayName("이기면 70,000, 연승이 이어질수록 1승마다 +10,000 (보너스는 최대 +100,000)")
    void winCash() {
        assertEquals(70_000, StreakRewards.cash(GameResult.WIN, 1, 10));
        assertEquals(80_000, StreakRewards.cash(GameResult.WIN, 2, 10));
        assertEquals(170_000, StreakRewards.cash(GameResult.WIN, 11, 10));
        assertEquals(170_000, StreakRewards.cash(GameResult.WIN, 30, 10));
    }

    @Test
    @DisplayName("지면 0, 이기면 턴이 적어도 준다 (딜러전이라 짜고 끝낼 수 없다)")
    void loseOrShortGame() {
        assertEquals(0, StreakRewards.cash(GameResult.LOSE, 0, 10));
        assertEquals(70_000, StreakRewards.cash(GameResult.WIN, 1, 2));
    }
}
