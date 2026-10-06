package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.FirePacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 FIRE 처리 (DevGameRoom 참고). 정식 FireHandler가 생기면 지운다. */
@Component
@RequiredArgsConstructor
public class DevFireHandler implements PacketHandler<FirePacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.FIRE;
    }

    @Override
    public Class<FirePacket> dataType() {
        return FirePacket.class;
    }

    @Override
    public void handle(WsContext ctx, FirePacket data) {
        devMatchService.fire(ctx.userId(), data.target());
    }
}
