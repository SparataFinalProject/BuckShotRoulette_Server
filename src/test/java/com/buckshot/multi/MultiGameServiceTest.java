package com.buckshot.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.ItemGrabbed;
import com.buckshot.multi.MultiPackets.ItemPlaced;
import com.buckshot.multi.MultiPackets.RoundStart;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MultiGameServiceTest {

    private PacketSender sender;
    private MultiGameService game;

    @BeforeEach
    void setUp() {
        sender = mock(PacketSender.class);
        game = new MultiGameService(sender);
        game.create(List.of(new Member(1, "a"), new Member(2, "b"), new Member(3, "c")));
    }

    private <T> T last(long userId, String type, Class<T> cls) {
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(sender, atLeastOnce()).sendTo(eq(userId), eq(type), data.capture());
        return cls.cast(data.getValue());
    }

    private void enterAll() {
        for (long u = 1; u <= 3; u++) game.ready(u);
        for (long u = 1; u <= 3; u++) game.introDone(u);
    }

    @Test
    @DisplayName("모두 씬에 들어와야 라운드가 시작되고, 모든 자리가 같은 개수를 받는다")
    void roundStartsWhenAllReady() {
        game.ready(1);
        game.ready(2);
        verify(sender, never()).sendTo(anyLong(), eq(PacketType.MULTI_ROUND_START), any());
        game.ready(3);
        RoundStart start = last(1, PacketType.MULTI_ROUND_START, RoundStart.class);
        assertEquals(3, start.itemCounts().length);
        for (int n : start.itemCounts()) assertEquals(MultiGameService.FIXED_ITEM_COUNT, n);
    }

    @Test
    @DisplayName("모두 연출을 마치면 아이템 받기가 시작되고, 꺼낸 아이템을 놓으면 모두에게 알린다")
    void grabAndPlace() {
        enterAll();
        verify(sender, atLeastOnce()).sendTo(eq(2L), eq(PacketType.MULTI_ITEM_PHASE_BEGIN), any());

        game.grabItem(1);
        ItemGrabbed grabbed = last(3, PacketType.MULTI_ITEM_GRABBED, ItemGrabbed.class);
        assertEquals(0, grabbed.seat());
        assertTrue(grabbed.itemId() >= 1 && grabbed.itemId() <= 10 && grabbed.itemId() != 7);

        game.placeItem(1, 4);
        ItemPlaced placed = last(2, PacketType.MULTI_ITEM_PLACED, ItemPlaced.class);
        assertEquals(0, placed.seat());
        assertEquals(4, placed.cell());
        assertEquals(grabbed.itemId(), placed.itemId());
    }

    @Test
    @DisplayName("꺼내지 않은 채 놓거나, 이미 찬 칸에 놓으면 무시한다")
    void invalidPlaceIgnored() {
        enterAll();
        game.placeItem(1, 0);
        verify(sender, never()).sendTo(anyLong(), eq(PacketType.MULTI_ITEM_PLACED), any());
        game.grabItem(1);
        game.placeItem(1, 0);
        game.grabItem(1);
        game.placeItem(1, 0);   // 같은 칸
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.times(1)).sendTo(eq(1L), eq(PacketType.MULTI_ITEM_PLACED), data.capture());
    }

    @Test
    @DisplayName("받을 개수를 다 놓으면 그 자리의 아이템 받기가 끝난다")
    void finishesAfterTarget() {
        enterAll();
        int target = last(1, PacketType.MULTI_ROUND_START, RoundStart.class).itemCounts()[0];
        for (int i = 0; i < target; i++) {
            game.grabItem(1);
            game.placeItem(1, i);
        }
        verify(sender, atLeastOnce()).sendTo(eq(2L), eq(PacketType.MULTI_ITEM_PHASE_DONE), any());
        ItemPlaced lastPlaced = last(1, PacketType.MULTI_ITEM_PLACED, ItemPlaced.class);
        assertTrue(lastPlaced.last());
    }
}
