package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.StealItemPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 1대1 아드레날린의 STEAL_ITEM (DevGameRoom.stealItem). */
@Component
@RequiredArgsConstructor
public class DevStealItemHandler implements PacketHandler<StealItemPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.STEAL_ITEM;
    }

    @Override
    public Class<StealItemPacket> dataType() {
        return StealItemPacket.class;
    }

    @Override
    public void handle(WsContext ctx, StealItemPacket data) {
        devMatchService.stealItem(ctx.userId(), data.slot());
    }
}
