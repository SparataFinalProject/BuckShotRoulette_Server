package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.GunPickupPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용: GUN_PICKUP을 상대에게 OPPONENT_GUN_PICKUP으로 전달한다 (DevGameRoom 참고). */
@Component
@RequiredArgsConstructor
public class DevGunPickupHandler implements PacketHandler<GunPickupPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.GUN_PICKUP;
    }

    @Override
    public Class<GunPickupPacket> dataType() {
        return GunPickupPacket.class;
    }

    @Override
    public void handle(WsContext ctx, GunPickupPacket data) {
        devMatchService.gunPickup(ctx.userId());
    }
}
