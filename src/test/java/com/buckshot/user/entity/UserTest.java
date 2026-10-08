package com.buckshot.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    private final User user = new User("test", 1000);

    @Test
    @DisplayName("새 유저는 현재·최고 연승이 0")
    void startsWithZeroStreak() {
        assertEquals(0, user.getCurrentStreak());
        assertEquals(0, user.getBestStreak());
    }

    @Test
    @DisplayName("이기면 현재 연승이 1씩 오른다")
    void winIncreasesCurrentStreak() {
        user.winStreakGame();
        assertEquals(1, user.getCurrentStreak());
        user.winStreakGame();
        assertEquals(2, user.getCurrentStreak());
    }

    @Test
    @DisplayName("이기는 순간 최고 기록도 갱신된다 (중간에 나가도 남음)")
    void winUpdatesBestStreakImmediately() {
        user.winStreakGame();
        assertEquals(1, user.getBestStreak());
        user.winStreakGame();
        assertEquals(2, user.getBestStreak());
    }

    @Test
    @DisplayName("지면 현재 연승은 0, 최고 기록은 유지")
    void loseResetsCurrentButKeepsBest() {
        user.winStreakGame();
        user.winStreakGame();
        user.loseStreakGame();
        assertEquals(0, user.getCurrentStreak());
        assertEquals(2, user.getBestStreak());
    }

    @Test
    @DisplayName("더 짧은 연승은 최고 기록을 낮추지 않는다")
    void bestStreakNotLoweredBySmallerRun() {
        user.winStreakGame();
        user.winStreakGame();
        user.winStreakGame();
        user.loseStreakGame();
        user.winStreakGame();
        user.winStreakGame();

        assertEquals(2, user.getCurrentStreak());
        assertEquals(3, user.getBestStreak());
    }

    @Test
    @DisplayName("더 긴 연승이면 최고 기록이 바뀐다")
    void bestStreakRaisedByLongerRun() {
        user.winStreakGame();
        user.winStreakGame();
        user.loseStreakGame();
        user.winStreakGame();
        user.winStreakGame();
        user.winStreakGame();
        user.winStreakGame();

        assertEquals(4, user.getCurrentStreak());
        assertEquals(4, user.getBestStreak());
    }
}