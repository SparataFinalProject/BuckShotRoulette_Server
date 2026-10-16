package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 개발용 임시 PLACE_READY 처리: 클라가 아이템 상자를 열었다 (DevGameRoom.placeReady). */
@Component
@RequiredArgsConstructor
public class DevPlaceReadyHandler implements PacketHandler<DevRoomPackets.Empty> {

    private final DevMatchService devMatchService;

    @Override
    public String type() {
        return PacketType.PLACE_READY;
    }

    @Override
    public Class<DevRoomPackets.Empty> dataType() {
        return DevRoomPackets.Empty.class;
    }

    @Override
    public void handle(WsContext ctx, DevRoomPackets.Empty data) {
        devMatchService.placeReady(ctx.userId());
    }
}
