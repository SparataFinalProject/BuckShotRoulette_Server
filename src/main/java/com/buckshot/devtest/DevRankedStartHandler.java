package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.RankedStartPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 연승 모드 시작 (RANKED_START). 딜러와 바로 MATCH_FOUND (DevMatchService.startRanked). */
@Component
@RequiredArgsConstructor
public class DevRankedStartHandler implements PacketHandler<RankedStartPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.RANKED_START;
    }

    @Override
    public Class<RankedStartPacket> dataType() {
        return RankedStartPacket.class;
    }

    @Override
    public void handle(WsContext ctx, RankedStartPacket data) {
        devMatchService.startRanked(ctx.userId());
    }
}
