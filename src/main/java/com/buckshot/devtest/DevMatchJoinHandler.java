package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.MatchJoinPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 MATCH_JOIN 처리 (DevMatchService 참고). 정식 MatchJoinHandler가 생기면 지운다. */
@Component
@RequiredArgsConstructor
public class DevMatchJoinHandler implements PacketHandler<MatchJoinPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.MATCH_JOIN;
    }

    @Override
    public Class<MatchJoinPacket> dataType() {
        return MatchJoinPacket.class;
    }

    @Override
    public void handle(WsContext ctx, MatchJoinPacket data) {
        devMatchService.join(ctx.userId());
    }
}
