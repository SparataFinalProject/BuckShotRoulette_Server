package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.service.StreakService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.ShellType;
import com.buckshot.ws.packet.s2c.RankedGameOverPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 연승 모드 한 판의 기록 모으기와 저장. 방의 알림(콜백)을 직접 불러 확인한다 (딜러 행동 스레드는 돌리지 않는다).
 */
class DevStreakGameTest {

    private static final long USER = 1L;
    private static final long DEALER = DevStreakGame.DEALER_ID;

    private final StreakService streakService = mock(StreakService.class);
    private final DevStreakGame game = new DevStreakGame(USER, streakService);

    DevStreakGameTest() {
        game.attach(new DevGameRoom(mock(PacketSender.class), USER, DEALER, game));   // begin 안 함: 체력만 쓴다
    }

    @Test
    @DisplayName("사람의 턴·사격·맞는 판단·받은 피해·아이템을 모아 판이 끝나면 저장한다")
    void recordsHumanStatsAndSavesOnGameOver() {
        when(streakService.onGameEnd(eq(USER), eq(GameResult.WIN), any()))
                .thenReturn(new StreakResult(3, 5, false, 26));

        game.turnStarted(USER);
        game.turnStarted(DEALER);                              // 딜러 차례는 세지 않는다
        game.turnStarted(USER);
        game.fired(USER, DEALER, ShellType.LIVE, 1);          // 상대 + 실탄: 맞음
        game.fired(USER, USER, ShellType.LIVE, 1);            // 나 + 실탄: 틀림, 피해 1
        game.fired(USER, USER, ShellType.BLANK, 0);           // 나 + 공포탄: 맞음
        game.fired(DEALER, USER, ShellType.LIVE, 2);          // 딜러가 쏨: 사격은 안 세고 피해만 2
        game.itemUsed(USER, "BEER", null, ShellType.BLANK);
        game.itemUsed(DEALER, "SAW", null, "");               // 딜러 아이템은 세지 않는다

        RankedGameOverPacket packet = (RankedGameOverPacket) game.gameOver(USER, GameOverReason.HP_ZERO);

        ArgumentCaptor<GameSummary> captor = ArgumentCaptor.forClass(GameSummary.class);
        verify(streakService).onGameEnd(eq(USER), eq(GameResult.WIN), captor.capture());
        GameSummary summary = captor.getValue();
        assertEquals(2, summary.turns());
        assertEquals(3, summary.shots());
        assertEquals(2, summary.correctShots());
        assertEquals(3, summary.damageTaken());
        assertEquals(GameRules.MAX_HP, summary.hpLeft());     // 방의 체력은 그대로 (begin 안 함)
        assertEquals(1, summary.itemUsage().get(Item.BEER));
        assertEquals(null, summary.itemUsage().get(Item.SAW));

        assertTrue(packet.recorded());
        assertEquals(DevStreakGame.MODE, packet.mode());
        assertEquals(3, packet.streak());
        assertEquals(5, packet.bestStreak());
        assertEquals(26, packet.expGained());
    }

    @Test
    @DisplayName("딜러가 이기면(또는 내가 나가면) LOSE로 저장한다")
    void dealerWinIsLose() {
        when(streakService.onGameEnd(eq(USER), eq(GameResult.LOSE), any()))
                .thenReturn(new StreakResult(0, 5, false, 5));

        RankedGameOverPacket packet = (RankedGameOverPacket) game.gameOver(DEALER, GameOverReason.DISCONNECT);

        assertEquals(0, packet.streak());
        assertEquals(DEALER, packet.winnerUserId());
    }

    @Test
    @DisplayName("저장에 실패해도 GAME_OVER는 보낸다 (recorded false)")
    void recordFailureStillSendsGameOver() {
        when(streakService.onGameEnd(eq(USER), any(), any())).thenThrow(new IllegalStateException("db down"));

        RankedGameOverPacket packet = (RankedGameOverPacket) game.gameOver(USER, GameOverReason.HP_ZERO);

        assertFalse(packet.recorded());
        assertEquals(USER, packet.winnerUserId());
    }
}
