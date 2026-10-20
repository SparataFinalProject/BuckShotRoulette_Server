package com.buckshot.matchmaking.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buckshot.devtest.DevMatchService;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.multi.MultiGameService;
import com.buckshot.rating.RatingService;
import com.buckshot.streak.service.StreakService;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GameReadyPacket;
import com.buckshot.ws.packet.c2s.MatchJoinPacket;
import com.buckshot.ws.packet.s2c.GameOverPacket;
import com.buckshot.ws.packet.s2c.MatchFoundPacket;
import com.buckshot.ws.session.SessionRegistry;
import com.buckshot.ws.session.UserDisconnectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * MATCH_JOIN(mode) -> 매칭 -> GAME_READY -> 게임 -> GAME_OVER까지 실제 핸들러·서비스를 이어서 확인한다.
 * 패킷 전송, 유저, 레이팅 저장소만 가짜.
 */
class RankedMatchFlowTest {

    private PacketSender sender;
    private RatingService rating;
    private MatchJoinHandler join;
    private GameReadyHandler ready;
    private DevMatchService dev;

    @BeforeEach
    void setUp() {
        sender = mock(PacketSender.class);
        rating = mock(RatingService.class);
        UserService users = mock(UserService.class);
        when(users.getProfile(anyLong())).thenAnswer(i ->
                new PlayerProfileResponse(i.getArgument(0), "p" + i.getArgument(0), 0, 0, 1000, "SILVER"));
        MatchmakingService matchmaking = new MatchmakingService();
        dev = new DevMatchService(sender, users, mock(SessionRegistry.class), matchmaking,
                mock(StreakService.class), mock(MultiGameService.class), rating);
        join = new MatchJoinHandler(matchmaking, sender, users, new MatchFoundSender(sender, users));
        ready = new GameReadyHandler(matchmaking, dev);
    }

    /** 두 사람이 같은 mode로 MATCH_JOIN을 보내 매칭되고 둘 다 준비해 게임이 시작될 때까지. gameId를 돌려준다. */
    private String startGame(String mode) {
        join.handle(new WsContext(1L), new MatchJoinPacket(mode));
        join.handle(new WsContext(2L), new MatchJoinPacket(mode));

        ArgumentCaptor<Object> found = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(1L), eq(PacketType.MATCH_FOUND), found.capture());
        String gameId = ((MatchFoundPacket) found.getValue()).gameId();
        verify(sender).sendTo(eq(2L), eq(PacketType.MATCH_FOUND), any());

        ready.handle(new WsContext(1L), new GameReadyPacket(gameId));
        ready.handle(new WsContext(2L), new GameReadyPacket(gameId));
        verify(sender).sendTo(eq(1L), eq(PacketType.GAME_START), any());
        verify(sender).sendTo(eq(2L), eq(PacketType.GAME_START), any());
        return gameId;
    }

    private GameOverPacket gameOverTo(long userId) {
        ArgumentCaptor<Object> over = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(userId), eq(PacketType.GAME_OVER), over.capture());
        return (GameOverPacket) over.getValue();
    }

    @Test
    @DisplayName("랭크 매칭: 끝나면 레이팅이 반영되고 GAME_OVER에 두 사람의 레이팅 변화가 담긴다 (시작하자마자 나가면 나간 사람만 깎임)")
    void rankedGameAppliesRating() {
        when(rating.applyEarlyLeave(2L, 1L)).thenReturn(new RatingService.RatedResult(
                new RatingService.Change(2L, 0, 0, 0, 1000),
                new RatingService.Change(1L, 0, 1, -16, 984)));

        startGame("RANKED");
        dev.onDisconnected(new UserDisconnectedEvent(1L));   // 1번이 나가면 2번 승리 (5턴 전이라 2번은 레이팅이 오르지 않는다)

        GameOverPacket packet = gameOverTo(2L);
        assertEquals(2L, packet.winnerUserId());
        assertEquals(GameOverReason.DISCONNECT, packet.reason());
        assertTrue(packet.recorded());
        assertEquals(2, packet.results().length);
        assertEquals(2L, packet.results()[0].userId());
        assertEquals(0, packet.results()[0].ratingDelta());
        assertEquals(1000, packet.results()[0].rating());
        verify(rating, never()).applyResult(anyLong(), anyLong());
        assertEquals(1L, packet.results()[1].userId());
        assertEquals(-16, packet.results()[1].ratingDelta());
        assertEquals(984, packet.results()[1].rating());
    }

    @Test
    @DisplayName("바로 매칭(mode 없음): 레이팅을 건드리지 않고 recorded=false")
    void quickGameDoesNotTouchRating() {
        startGame(null);
        dev.onDisconnected(new UserDisconnectedEvent(1L));

        GameOverPacket packet = gameOverTo(2L);
        assertFalse(packet.recorded());
        assertEquals(0, packet.results().length);
        verify(rating, never()).applyResult(anyLong(), anyLong());
    }

    @Test
    @DisplayName("MATCH_JOIN JSON의 mode가 제대로 읽힌다 (RANKED / 없음 / 소문자)")
    void joinPacketParsesMode() {
        JsonMapper mapper = JsonMapper.builder().build();

        assertTrue(mapper.readValue("{\"mode\":\"RANKED\"}", MatchJoinPacket.class).ranked());
        assertTrue(mapper.readValue("{\"mode\":\"ranked\"}", MatchJoinPacket.class).ranked());
        assertFalse(mapper.readValue("{}", MatchJoinPacket.class).ranked());
        assertNull(mapper.readValue("{\"mode\":null}", MatchJoinPacket.class).mode());
    }
}
