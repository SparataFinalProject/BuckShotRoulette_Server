package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.dto.ItemSlot;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 1대1 방의 도박 동전: 가진 아이템을 전부 돌려보내고 같은 개수를 (동전 없이) 새로 채운다. */
class DevGameRoomCoinTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private final PacketSender sender = mock(PacketSender.class);
    private final DevGameRoom room = new DevGameRoom(sender, A, B, DevGameRoom.Listener.NONE, new Random(7));

    private long startAndGetTurnUser() {
        room.begin();
        return room.turnUserId();
    }

    private void give(long userId, int slot, String item) throws Exception {
        var field = DevGameRoom.class.getDeclaredField("items");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<Long, String[]>) field.get(room);
        map.get(userId)[slot] = item;
    }

    @Test
    @DisplayName("동전을 쓰면 가진 아이템 수만큼 동전이 아닌 새 아이템을 칸 없이 알리고, 플레이어가 칸을 골라 놓는다 (상대에게 ITEM_PLACED)")
    void drawsNewItemsAndPlayerPlacesThem() throws Exception {
        long user = startAndGetTurnUser();
        long other = user == A ? B : A;
        give(user, 0, ItemType.GAMBLE_COIN);
        give(user, 2, ItemType.BEER);
        give(user, 3, ItemType.SAW);
        give(user, 6, ItemType.CIGARETTE);

        assertTrue(room.useItem(user, 0));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(user), eq(PacketType.ITEM_USED), captor.capture());
        verify(sender).sendTo(eq(other), eq(PacketType.ITEM_USED), any());
        var packet = (DevGameRoom.DevItemUsedPacket) captor.getValue();
        assertEquals(ItemType.GAMBLE_COIN, packet.itemType());
        assertEquals(3, packet.items().length);
        for (ItemSlot s : packet.items()) {
            assertEquals(-1, s.slot());   // 칸은 플레이어가 고른다
            assertNotEquals(ItemType.GAMBLE_COIN, s.itemType());   // 새로 뽑을 때 동전은 나오지 않는다
        }
        for (String s : room.itemsOf(user)) assertNull(s);   // 전부 돌려보냈다 (동전 칸도 비었다)

        // 놓는 동안은 쏘거나 아이템을 쓸 수 없다
        assertFalse(room.fire(user, "OPPONENT"));
        verify(sender).sendTo(eq(user), eq(PacketType.ERROR), any());

        // 원하는 칸에 하나씩 놓는다: 상대에게만 ITEM_PLACED (auto false), 이미 찬 칸은 거절
        room.placeItem(user, 5);
        room.placeItem(user, 5);   // 같은 칸 -> INVALID_SLOT
        room.placeItem(user, 1);
        room.placeItem(user, 7);
        assertEquals(packet.items()[0].itemType(), room.itemsOf(user)[5]);
        assertEquals(packet.items()[1].itemType(), room.itemsOf(user)[1]);
        assertEquals(packet.items()[2].itemType(), room.itemsOf(user)[7]);
        verify(sender, org.mockito.Mockito.times(3)).sendTo(eq(other), eq(PacketType.ITEM_PLACED), any());
        verify(sender, never()).sendTo(eq(user), eq(PacketType.ITEM_PLACED), any());

        // 다 놓으면 차례는 그대로고 다시 쏠 수 있다. 더 놓으려 하면 거절
        assertEquals(user, room.turnUserId());
        room.placeItem(user, 4);
        assertNull(room.itemsOf(user)[4]);
        assertTrue(room.fire(user, "OPPONENT"));
    }

    @Test
    @DisplayName("돌려보낼 아이템이 없으면 동전을 쓸 수 없고 동전도 그대로다")
    void rejectedWhenNothingToReturn() throws Exception {
        long user = startAndGetTurnUser();
        give(user, 4, ItemType.GAMBLE_COIN);

        assertFalse(room.useItem(user, 4));

        verify(sender, never()).sendTo(eq(user), eq(PacketType.ITEM_USED), any());
        assertEquals(ItemType.GAMBLE_COIN, room.itemsOf(user)[4]);
    }
}
