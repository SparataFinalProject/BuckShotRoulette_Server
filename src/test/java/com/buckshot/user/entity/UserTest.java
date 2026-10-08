package com.buckshot.user.entity;

import com.buckshot.streak.GameSummary;
import com.buckshot.streak.entity.StreakRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private final User user = new User("test", 1000);
    private final LocalDateTime first = LocalDateTime.of(2026, 10, 1, 12, 0);
    private final LocalDateTime later = LocalDateTime.of(2026, 10, 2, 12, 0);

    /**
     * 판마다 turnsPerWin 턴씩 써서 wins번 이긴 도전.
     */
    private StreakRun runOf(int wins, int turnsPerWin) {
        StreakRun run = new StreakRun(1L, 1, first);
        for (int i = 0; i < wins; i++) {
            run.recordWin(new GameSummary(turnsPerWin, 5, 4, 1, 3, Map.of()));
        }
        return run;
    }

    @Test
    @DisplayName("새 유저는 최고 기록이 없다")
    void startsWithNoBest() {
        assertEquals(0, user.getBestStreak());
        assertEquals(0, user.getBestStreakTurns());
        assertNull(user.getBestAchievedAt());
        assertNull(user.getBestRunId());
    }

    @Test
    @DisplayName("더 긴 연승이면 최고 기록이 되고, 연승·턴·달성 시각이 함께 저장된다")
    void longerRunBecomesBest() {
        assertTrue(user.offerBest(runOf(2, 10), first));

        assertEquals(2, user.getBestStreak());
        assertEquals(20, user.getBestStreakTurns());
        assertEquals(first, user.getBestAchievedAt());
    }

    @Test
    @DisplayName("연승이 같으면 턴이 더 적은 쪽이 최고 기록이 된다")
    void sameWinsFewerTurnsBecomesBest() {
        user.offerBest(runOf(3, 10), first);   // 3승 30턴

        assertTrue(user.offerBest(runOf(3, 7), later));   // 3승 21턴

        assertEquals(3, user.getBestStreak());
        assertEquals(21, user.getBestStreakTurns());
        assertEquals(later, user.getBestAchievedAt());
    }

    @Test
    @DisplayName("연승이 같고 턴이 더 많으면 무시한다")
    void sameWinsMoreTurnsIsIgnored() {
        user.offerBest(runOf(3, 10), first);   // 3승 30턴

        assertFalse(user.offerBest(runOf(3, 15), later));   // 3승 45턴

        assertEquals(30, user.getBestStreakTurns());
        assertEquals(first, user.getBestAchievedAt());
    }

    @Test
    @DisplayName("연승도 턴도 같으면 무시한다 (먼저 세운 기록이 남는다)")
    void sameWinsSameTurnsIsIgnored() {
        user.offerBest(runOf(3, 10), first);

        assertFalse(user.offerBest(runOf(3, 10), later));

        assertEquals(first, user.getBestAchievedAt());
    }

    @Test
    @DisplayName("더 짧은 연승은 턴이 적어도 최고 기록을 바꾸지 않는다")
    void shorterRunIsIgnored() {
        user.offerBest(runOf(3, 10), first);   // 3승 30턴

        assertFalse(user.offerBest(runOf(2, 1), later));   // 2승 2턴

        assertEquals(3, user.getBestStreak());
        assertEquals(30, user.getBestStreakTurns());
    }

    @Test
    @DisplayName("같은 도전이 이길 때마다 들어오면 최고 기록이 계속 늘어난다")
    void sameRunGrowsBest() {
        StreakRun run = new StreakRun(1L, 1, first);

        run.recordWin(new GameSummary(10, 5, 4, 1, 3, Map.of()));
        assertTrue(user.offerBest(run, first));
        run.recordWin(new GameSummary(10, 5, 4, 1, 3, Map.of()));
        assertTrue(user.offerBest(run, later));

        assertEquals(2, user.getBestStreak());
        assertEquals(20, user.getBestStreakTurns());
        assertEquals(later, user.getBestAchievedAt());
    }
}