package com.buckshot.matchmaking.ws;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GameReadyPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameReadyHandler
        implements PacketHandler<GameReadyPacket> {

    private final MatchmakingService matchmakingService;

    @Override
    public String type() {
        return PacketType.GAME_READY;
    }

    @Override
    public Class<GameReadyPacket> dataType() {
        return GameReadyPacket.class;
    }

    @Override
    public void handle(
            WsContext ctx,
            GameReadyPacket data
    ) {
        if (data == null ||
                data.gameId() == null ||
                data.gameId().isBlank()) {

            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        long userId = ctx.userId();

        MatchmakingService.ReadyResult result =
                matchmakingService.markReady(
                        userId,
                        data.gameId()
                );

        if (!result.newlyReady()) {
            log.info(
                    "duplicate GAME_READY ignored gameId={} userId={}",
                    result.gameId(),
                    userId
            );

            return;
        }

        log.info(
                "game ready gameId={} userId={}",
                result.gameId(),
                userId
        );

        if (!result.allReady()) {
            return;
        }

        log.info(
                "all players ready gameId={} playerA={} playerB={}",
                result.gameId(),
                result.playerAId(),
                result.playerBId()
        );

        // 아직 여기서 GAME_START는 보내지 않는다.
        // 실제 게임 상태 생성 파트와 연결 후 처리한다.
    }
}