package com.buckshot.streak.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakGameTest {

    private final LocalDateTime endedAt = LocalDateTime.of(2026, 10, 8, 12, 0);

    @Test
    @DisplayName("판 요약의 값이 기록에 그대로 들어간다")
    void copiesSummaryValues() {
        GameSummary summary = new GameSummary(12, 6, 5, 2, 4, Map.of());

        StreakGame game = new StreakGame(7L, 3, GameResult.LOSE, summary, endedAt);

        assertEquals(7L, game.getRunId());
        assertEquals(3, game.getGameIndex());
        assertEquals(GameResult.LOSE, game.getResult());
        assertEquals(12, game.getTurns());
        assertEquals(6, game.getShots());
        assertEquals(5, game.getCorrectShots());
        assertEquals(2, game.getDamageTaken());
        assertEquals(4, game.getHpLeft());
        assertEquals(endedAt, game.getEndedAt());
    }

    @Test
    @DisplayName("이 판에서 쓴 아이템이 그대로 들어간다")
    void copiesItemUsage() {
        GameSummary summary = new GameSummary(12, 6, 5, 2, 4, Map.of(Item.BEER, 2, Item.SAW, 1));

        StreakGame game = new StreakGame(7L, 1, GameResult.WIN, summary, endedAt);

        assertEquals(Map.of(Item.BEER, 2, Item.SAW, 1), game.getItemUsage());
    }

    @Test
    @DisplayName("넘긴 아이템 Map을 나중에 바꿔도 기록은 그대로")
    void laterChangesToSummaryMapDoNotAffectGame() {
        Map<Item, Integer> items = new EnumMap<>(Item.class);
        items.put(Item.BEER, 2);
        StreakGame game = new StreakGame(7L, 1, GameResult.WIN,
                new GameSummary(12, 6, 5, 2, 4, items), endedAt);

        items.put(Item.BEER, 99);

        assertEquals(2, game.getItemUsage().get(Item.BEER));
    }
}
