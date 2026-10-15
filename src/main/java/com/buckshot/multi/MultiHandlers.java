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

    @Bean
    PacketHandler<DevRoomPackets.Empty> multiPickupShotgunHandler(MultiGameService game) {
        return handler(PacketType.MULTI_PICKUP_SHOTGUN, DevRoomPackets.Empty.class, (ctx, d) -> game.pickupShotgun(ctx.userId()));
    }

    @Bean
    PacketHandler<MultiPackets.Look> multiLookHandler(MultiGameService game) {
        return handler(PacketType.MULTI_LOOK, MultiPackets.Look.class, (ctx, d) -> game.look(ctx.userId(), d.direction()));
    }

    @Bean
    PacketHandler<MultiPackets.Shoot> multiShootHandler(MultiGameService game) {
        return handler(PacketType.MULTI_SHOOT, MultiPackets.Shoot.class, (ctx, d) -> game.shoot(ctx.userId(), d.target()));
    }

    @Bean
    PacketHandler<MultiPackets.UseItem> multiUseItemHandler(MultiGameService game) {
        return handler(PacketType.MULTI_USE_ITEM, MultiPackets.UseItem.class, (ctx, d) -> game.useItem(ctx.userId(), d.cell()));
    }

    @Bean
    PacketHandler<MultiPackets.ItemSecondary> multiItemSecondaryHandler(MultiGameService game) {
        return handler(PacketType.MULTI_ITEM_SECONDARY, MultiPackets.ItemSecondary.class, (ctx, d) -> game.itemSecondary(ctx.userId(), d.target(), d.cell()));
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
