package com.buckshot.multi;

import com.buckshot.devtest.DevRoomPackets;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import java.util.function.BiConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 3~4인 전용 게임 패킷 처리 (MultiGameService 참고) */
@Configuration
public class MultiHandlers {

    @Bean
    PacketHandler<DevRoomPackets.Empty> multiReadyHandler(MultiGameService game) {
        return handler(PacketType.MULTI_READY, DevRoomPackets.Empty.class, (ctx, d) -> game.ready(ctx.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Empty> multiIntroDoneHandler(MultiGameService game) {
        return handler(PacketType.MULTI_INTRO_DONE, DevRoomPackets.Empty.class, (ctx, d) -> game.introDone(ctx.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Empty> multiGrabItemHandler(MultiGameService game) {
        return handler(PacketType.MULTI_GRAB_ITEM, DevRoomPackets.Empty.class, (ctx, d) -> game.grabItem(ctx.userId()));
    }

    @Bean
    PacketHandler<MultiPackets.PlaceItem> multiPlaceItemHandler(MultiGameService game) {
        return handler(PacketType.MULTI_PLACE_ITEM, MultiPackets.PlaceItem.class, (ctx, d) -> game.placeItem(ctx.userId(), d.cell()));
    }

    @Bean
    PacketHandler<MultiPackets.Chat> multiChatHandler(MultiGameService game) {
        return handler(PacketType.MULTI_CHAT, MultiPackets.Chat.class, (ctx, d) -> game.chat(ctx.userId(), d.text()));
    }

    private static <T> PacketHandler<T> handler(String type, Class<T> dataType, BiConsumer<WsContext, T> handle) {
        return new PacketHandler<>() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Class<T> dataType() {
                return dataType;
            }

            @Override
            public void handle(WsContext ctx, T data) {
                handle.accept(ctx, data);
            }
        };
    }
}
