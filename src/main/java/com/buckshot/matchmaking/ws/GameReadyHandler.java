package com.buckshot.matchmaking.ws;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GameReadyPacket;
import com.buckshot.devtest.DevMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameReadyHandler
        implements PacketHandler<GameReadyPacket> {

    private final MatchmakingService matchmakingService;
    private final DevMatchService devMatchService;

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
        devMatchService.rememberTestOptions(userId, data.test());   // 개발용 테스트 옵션 (아이템 고정, 첫 턴 아이템). 방을 만들 때 쓴다

        // 방에서 시작한 1대1, 연승 모드 게임은 DevMatchService가 만든 게임이라 그쪽에서 준비를 받는다
        if (devMatchService.ownsGame(data.gameId())) {
            devMatchService.ready(userId, data.gameId());
            return;
        }

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

        // 정식 매칭 결과로 기존 게임 진행 시스템 시작
        devMatchService.startFromMatchmaking(
                result.gameId(),
                result.playerAId(),
                result.playerBId()
        );

        // 게임 시작이 성공한 뒤 준비 단계의 매칭 정보 정리
        matchmakingService.markGameStarted(result.gameId());
    }
}