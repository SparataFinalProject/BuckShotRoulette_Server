package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.TestPacket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestPacketHandler implements PacketHandler<TestPacket> {

    private final PacketSender packetSender;

    @Override
    public String type() {
        return PacketType.TEST;
    }

    @Override
    public Class<TestPacket> dataType() {
        return TestPacket.class;
    }

    @Override
    public void handle(WsContext ctx, TestPacket data) {
        packetSender.sendTo(ctx.userId(), PacketType.TEST,
                new com.buckshot.ws.packet.s2c.TestPacket("echo: " + data.message(), System.currentTimeMillis()));
    }
}
