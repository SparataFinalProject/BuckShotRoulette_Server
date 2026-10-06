package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.UseItemPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 USE_ITEM 처리 (DevGameRoom 참고). 정식 UseItemHandler가 생기면 지운다. */
@Component
@RequiredArgsConstructor
public class DevUseItemHandler implements PacketHandler<UseItemPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.USE_ITEM;
    }

    @Override
    public Class<UseItemPacket> dataType() {
        return UseItemPacket.class;
    }

    @Override
    public void handle(WsContext ctx, UseItemPacket data) {
        devMatchService.useItem(ctx.userId(), data.slot());
    }
}
