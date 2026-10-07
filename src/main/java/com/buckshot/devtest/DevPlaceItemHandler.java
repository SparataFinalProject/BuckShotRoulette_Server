package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.PlaceItemPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 PLACE_ITEM 처리 (DevGameRoom 참고). */
@Component
@RequiredArgsConstructor
public class DevPlaceItemHandler implements PacketHandler<PlaceItemPacket> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.PLACE_ITEM;
    }

    @Override
    public Class<PlaceItemPacket> dataType() {
        return PlaceItemPacket.class;
    }

    @Override
    public void handle(WsContext ctx, PlaceItemPacket data) {
        devMatchService.placeItem(ctx.userId(), data.slot());
    }
}
