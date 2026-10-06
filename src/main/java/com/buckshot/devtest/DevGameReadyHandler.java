package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GameReadyPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 GAME_READY 처리 (DevMatchService 참고). 정식 GameReadyHandler가 생기면 지운다. */
@Component
@RequiredArgsConstructor
public class DevGameReadyHandler implements PacketHandler<GameReadyPacket> {

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
    public void handle(WsContext ctx, GameReadyPacket data) {
        devMatchService.ready(ctx.userId(), data.gameId());
    }
}
