package com.buckshot.matchmaking.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buckshot.devtest.DevMatchService;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.multi.MultiGameService;
import com.buckshot.streak.service.StreakService;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GameReadyPacket;
import com.buckshot.ws.packet.s2c.MatchFoundPacket;
import com.buckshot.ws.session.SessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameReadyHandlerTest {

    @Test
    @DisplayName("방에서 시작한 1대1 게임의 GAME_READY는 DevMatchService가 받아 GAME_START를 보낸다")
    void roomGameReadyGoesToDevMatchService() {
        PacketSender sender = mock(PacketSender.class);
        UserService users = mock(UserService.class);
        when(users.getProfile(anyLong())).thenAnswer(i -> new PlayerProfileResponse(i.getArgument(0), "p" + i.getArgument(0), 0, 0, 1000, "BRONZE"));
        MatchmakingService matchmaking = mock(MatchmakingService.class);
        DevMatchService dev = new DevMatchService(sender, users, mock(SessionRegistry.class), matchmaking,
                mock(StreakService.class), mock(MultiGameService.class));
        GameReadyHandler handler = new GameReadyHandler(matchmaking, dev);

        dev.startPair(1L, 2L);
        ArgumentCaptor<Object> found = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(1L), eq(PacketType.MATCH_FOUND), found.capture());
        String gameId = ((MatchFoundPacket) found.getValue()).gameId();

        handler.handle(new WsContext(1L), new GameReadyPacket(gameId));
        handler.handle(new WsContext(2L), new GameReadyPacket(gameId));

        verify(sender).sendTo(eq(1L), eq(PacketType.GAME_START), any());
        verify(sender).sendTo(eq(2L), eq(PacketType.GAME_START), any());
        verify(matchmaking, never()).markReady(anyLong(), anyString());
    }
}
