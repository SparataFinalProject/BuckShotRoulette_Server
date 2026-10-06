package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.AimPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용: AIM을 상대에게 OPPONENT_AIM으로 전달한다 (DevGameRoom 참고). */
@Component
@RequiredArgsConstructor
public class DevAimHandler implements PacketHandler<AimPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.AIM;
    }

    @Override
    public Class<AimPacket> dataType() {
        return AimPacket.class;
    }

    @Override
    public void handle(WsContext ctx, AimPacket data) {
        devMatchService.aim(ctx.userId(), data.target());
    }
}
