package com.buckshot.streak.entity;

import com.buckshot.game.Item;
import com.buckshot.streak.GameSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StreakRunTest {
    private final LocalDateTime first = LocalDateTime.of(2026, 10, 1, 12, 0);
    private final LocalDateTime later = LocalDateTime.of(2026, 10, 2, 12, 0);
    private final StreakRun run = new StreakRun(1L, 1, first);

    private GameSummary game(int turns, Map<Item, Integer> items) {
        return new GameSummary(turns, 5, 4, 1, 3, items);
    }

    @Test
    @DisplayName("새 도전은 진행 중이고 기록이 비어 있다")
    void newRunIsOngoingAndEmpty() {
        assertTrue(run.isOngoing());
        assertEquals(0, run.getWins());
        assertTrue(run.getItemUsage().isEmpty());
    }

    @Test
    @DisplayName("두 판을 이기면 숫자가 합쳐진다")
    void recordWinAddsUpTwoGames() {
        run.recordWin(game(10, Map.of()));
        run.recordWin(game(12, Map.of()));

        assertEquals(2, run.getWins());
        assertEquals(22, run.getTurns());
        assertEquals(10, run.getShots());
        assertEquals(8, run.getCorrectShots());
        assertEquals(2, run.getDamageTaken());
    }

    @Test
    @DisplayName("아이템은 종류별로 합쳐진다")
    void recordWinMergesItemsByKind() {
        run.recordWin(game(10, Map.of(Item.BEER, 2)));
        run.recordWin(game(12, Map.of(Item.BEER, 1, Item.SAW, 1)));

        assertEquals(3, run.getItemUsage().get(Item.BEER));
        assertEquals(1, run.getItemUsage().get(Item.SAW));
        assertNull(run.getItemUsage().get(Item.CIGARETTE));
    }

    @Test
    @DisplayName("끝내면 진행 중이 아니고 끝난 시각이 남는다")
    void endStopsRun() {
        run.end(later);

        assertFalse(run.isOngoing());
        assertNotNull(run.getEndedAt());
    }

    @Test
    @DisplayName("끝난 도전에는 승리를 기록할 수 없고 기록도 바뀌지 않는다")
    void cannotRecordWinAfterEnd() {
        run.recordWin(game(10, Map.of(Item.BEER, 2)));
        run.end(later);

        assertEquals(1, run.getWins());
        assertThrows(IllegalStateException.class,
                () -> run.recordWin(game(12, Map.of(Item.BEER, 2, Item.CIGARETTE, 1))));

        assertEquals(1, run.getWins());
    }

    @Test
    @DisplayName("넘긴 아이템 Map을 나중에 바꿔도 기록은 그대로")
    void laterChangesToSummaryMapDoNotAffectRun() {
        Map<Item, Integer> items = new EnumMap<>(Item.class);
        items.put(Item.BEER, 2);
        run.recordWin(game(10, items));

        items.put(Item.BEER, 99);

        assertEquals(2, run.getItemUsage().get(Item.BEER));
    }
}
