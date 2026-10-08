package com.buckshot.devtest;

import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import java.util.function.BiConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 개발용 방 패킷 처리 (DevRoomService 참고). 패킷마다 클래스를 만드는 대신 여기서 한 번에 등록한다. */
@Configuration
public class DevRoomHandlers {

    @Bean
    PacketHandler<DevRoomPackets.Empty> roomCreateHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_CREATE, DevRoomPackets.Empty.class, (ctx, d) -> rooms.create(ctx.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Join> roomJoinHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_JOIN, DevRoomPackets.Join.class, (ctx, d) -> rooms.join(ctx.userId(), d.roomId(), d.code()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Empty> roomLeaveHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_LEAVE, DevRoomPackets.Empty.class, (ctx, d) -> rooms.leave(ctx.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.SetFriendsOnly> roomFriendsOnlyHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_SET_FRIENDS_ONLY, DevRoomPackets.SetFriendsOnly.class,
                (ctx, d) -> rooms.setFriendsOnly(ctx.userId(), d.friendsOnly()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Kick> roomKickHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_KICK, DevRoomPackets.Kick.class, (ctx, d) -> rooms.kick(ctx.userId(), d.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.Empty> roomStartHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_START, DevRoomPackets.Empty.class, (ctx, d) -> rooms.start(ctx.userId()));
    }

    @Bean
    PacketHandler<DevRoomPackets.ListWatch> roomListWatchHandler(DevRoomService rooms) {
        return handler(PacketType.ROOM_LIST_WATCH, DevRoomPackets.ListWatch.class, (ctx, d) -> rooms.watch(ctx.userId(), d.watch()));
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
