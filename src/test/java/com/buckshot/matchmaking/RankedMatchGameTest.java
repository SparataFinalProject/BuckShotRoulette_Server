package com.buckshot.matchmaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buckshot.rating.RatingService;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.s2c.GameOverPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankedMatchGameTest {

    private final RatingService ratingService = mock(RatingService.class);
    private final RankedMatchGame game = new RankedMatchGame(10L, 20L, ratingService);

    @Test
    @DisplayName("GAME_OVER에 두 사람의 레이팅 변화가 담기고 recorded=true")
    void gameOverCarriesRatingChanges() {
        when(ratingService.applyResult(20L, 10L)).thenReturn(new RatingService.RatedResult(
                new RatingService.Change(20L, 3, 1, 16, 1016),
                new RatingService.Change(10L, 1, 4, -16, 984)));

        GameOverPacket packet = (GameOverPacket) game.gameOver(20L, GameOverReason.HP_ZERO);

        assertEquals(20L, packet.winnerUserId());
        assertEquals(GameOverReason.HP_ZERO, packet.reason());
        assertTrue(packet.recorded());
        assertEquals(2, packet.results().length);
        PlayerResult winner = packet.results()[0];
        PlayerResult loser = packet.results()[1];
        assertEquals(20L, winner.userId());
        assertEquals(16, winner.ratingDelta());
        assertEquals(1016, winner.rating());
        assertEquals(3, winner.wins());
        assertEquals(10L, loser.userId());
        assertEquals(-16, loser.ratingDelta());
        assertEquals(984, loser.rating());
        assertEquals(4, loser.losses());
    }

    @Test
    @DisplayName("보상 서비스가 있으면 GAME_OVER에 승자·패자가 받은 경험치와 돈이 담긴다")
    void gameOverCarriesRewards() {
        var rewards = mock(com.buckshot.reward.RewardService.class);
        var game = new RankedMatchGame(10L, 20L, ratingService, rewards);
        game.turnStarted(10L, 8, 0L);   // 8턴 진행
        when(ratingService.applyResult(20L, 10L)).thenReturn(new RatingService.RatedResult(
                new RatingService.Change(20L, 3, 1, 16, 1016),
                new RatingService.Change(10L, 1, 4, -16, 984)));

        GameOverPacket packet = (GameOverPacket) game.gameOver(20L, GameOverReason.HP_ZERO);

        assertEquals(2, packet.rewards().length);
        assertEquals(20L, packet.rewards()[0].userId());
        assertEquals(20, packet.rewards()[0].expGained());
        assertEquals(com.buckshot.reward.OneOnOneRewards.WIN_CASH, packet.rewards()[0].moneyGained());
        assertEquals(10L, packet.rewards()[1].userId());
        assertEquals(5, packet.rewards()[1].expGained());
        assertEquals(0, packet.rewards()[1].moneyGained());
    }

    @Test
    @DisplayName("나간 사람은 진 것으로 센다 (DISCONNECT)")
    void disconnectCountsAsLoss() {
        when(ratingService.applyResult(10L, 20L)).thenReturn(new RatingService.RatedResult(
                new RatingService.Change(10L, 1, 0, 16, 1016),
                new RatingService.Change(20L, 0, 1, -16, 984)));

        GameOverPacket packet = (GameOverPacket) game.gameOver(10L, GameOverReason.DISCONNECT);

        assertTrue(packet.recorded());
        assertEquals(-16, packet.results()[1].ratingDelta());
        assertEquals(20L, packet.results()[1].userId());
    }

    @Test
    @DisplayName("레이팅 저장에 실패해도 GAME_OVER는 나가고 recorded=false")
    void failureStillReportsGameOver() {
        when(ratingService.applyResult(20L, 10L)).thenThrow(new IllegalStateException("db down"));

        GameOverPacket packet = (GameOverPacket) game.gameOver(20L, GameOverReason.HP_ZERO);

        assertEquals(20L, packet.winnerUserId());
        assertFalse(packet.recorded());
        assertEquals(0, packet.results().length);
    }

    @Test
    @DisplayName("바로 매칭과 같은 아이템 구성(새 아이템 포함)을 쓴다")
    void usesNewItems() {
        assertTrue(game.newItems());
    }
}
