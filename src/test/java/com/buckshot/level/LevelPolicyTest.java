package com.buckshot.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.streak.GameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LevelPolicyTest {

    @Test
    @DisplayName("1대1: 이기면 20, 지면 5, 5턴 미만이면 0 (연승 보너스 없음)")
    void matchExpRules() {
        assertEquals(20, LevelPolicy.matchExp(true, 5));
        assertEquals(5, LevelPolicy.matchExp(false, 5));
        assertEquals(0, LevelPolicy.matchExp(true, 4));
        assertEquals(0, LevelPolicy.matchExp(false, 4));
    }

    @Test
    @DisplayName("싱글 클리어와 3~4인 라운드 승리는 20")
    void singleAndRoundExp() {
        assertEquals(20, LevelPolicy.singleClearExp());
        assertEquals(20, LevelPolicy.roundWinExp());
    }

    @Test
    @DisplayName("이기면 기본 20 + 연승 × 2 (1연승 22, 5연승 30)")
    void winGivesBaseAndStreakBonus() {
        assertEquals(22, LevelPolicy.streakExp(GameResult.WIN, 1, 12));
        assertEquals(30, LevelPolicy.streakExp(GameResult.WIN, 5, 12));
    }

    @Test
    @DisplayName("연승 보너스는 최대 +20 (10연승 40, 15연승도 40)")
    void streakBonusIsCapped() {
        assertEquals(40, LevelPolicy.streakExp(GameResult.WIN, 10, 12));
        assertEquals(40, LevelPolicy.streakExp(GameResult.WIN, 15, 12));
    }

    @Test
    @DisplayName("지면 5")
    void loseGivesSmallExp() {
        assertEquals(5, LevelPolicy.streakExp(GameResult.LOSE, 0, 12));
    }

    @Test
    @DisplayName("4턴이면 승리든 패배든 0, 5턴이면 받는다 (경계)")
    void tooShortGameGivesNoExp() {
        assertEquals(0, LevelPolicy.streakExp(GameResult.WIN, 3, 4));
        assertEquals(0, LevelPolicy.streakExp(GameResult.LOSE, 0, 4));
        assertTrue(LevelPolicy.streakExp(GameResult.WIN, 3, 5) > 0);
        assertTrue(LevelPolicy.streakExp(GameResult.LOSE, 0, 5) > 0);
    }

    @Test
    @DisplayName("누적 경험치로 레벨 계산: 경계값 앞뒤 (0→1, 99→1, 100→2, 299→2, 300→3, 599→3, 600→4)")
    void levelFromExp() {
        assertEquals(1, LevelPolicy.levelOf(0));
        assertEquals(1, LevelPolicy.levelOf(99));
        assertEquals(2, LevelPolicy.levelOf(100));
        assertEquals(2, LevelPolicy.levelOf(299));
        assertEquals(3, LevelPolicy.levelOf(300));
        assertEquals(3, LevelPolicy.levelOf(599));
        assertEquals(4, LevelPolicy.levelOf(600));
    }
}
