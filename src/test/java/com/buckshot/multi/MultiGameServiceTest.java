package com.buckshot.multi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.ChatMessage;
import com.buckshot.multi.MultiPackets.ItemGrabbed;
import com.buckshot.multi.MultiPackets.ItemPlaced;
import com.buckshot.multi.MultiPackets.LoadShotgun;
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

    @Test
    @DisplayName("채팅은 보낸 사람을 포함한 모두에게 보낸 자리 번호와 함께 간다")
    void chatReachesEveryone() {
        game.chat(2, "  안녕하세요  ");
        for (long u = 1; u <= 3; u++) {
            ChatMessage msg = last(u, PacketType.MULTI_CHAT_MSG, ChatMessage.class);
            assertEquals(1, msg.seat());
            assertEquals("안녕하세요", msg.text());
        }
    }

    @Test
    @DisplayName("빈 채팅은 버리고, 긴 채팅은 잘라 보내고, 연달아 보낸 채팅은 버린다")
    void chatIsSanitizedAndRateLimited() {
        game.chat(1, "   ");
        game.chat(1, "a".repeat(MultiGameService.CHAT_MAX_LENGTH + 50));
        game.chat(1, "너무 빨리");
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.times(1)).sendTo(eq(2L), eq(PacketType.MULTI_CHAT_MSG), data.capture());
        assertEquals(MultiGameService.CHAT_MAX_LENGTH, ((ChatMessage) data.getValue()).text().length());
    }

    @Test
    @DisplayName("아이템 받기가 끝나 게임이 정리된 뒤에도 채팅은 간다")
    void chatWorksAfterItemPhaseOver() {
        enterAll();
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, i);
            }
        }
        game.chat(3, "끝났다");
        assertEquals("끝났다", last(1, PacketType.MULTI_CHAT_MSG, ChatMessage.class).text());
    }

    @Test
    @DisplayName("모두 아이템 받기를 끝내고 2초 뒤 모든 자리에 같은 셸 순서(실탄이 앞)가 간다")
    void loadShotgunAfterItemPhase() {
        enterAll();
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, i);
            }
        }
        verify(sender, never()).sendTo(anyLong(), eq(PacketType.MULTI_LOAD_SHOTGUN), any());
        ArgumentCaptor<Object> first = ArgumentCaptor.forClass(Object.class);
        verify(sender, timeout(4000)).sendTo(eq(1L), eq(PacketType.MULTI_LOAD_SHOTGUN), first.capture());
        boolean[] seq = ((LoadShotgun) first.getValue()).sequence();
        assertTrue(seq.length >= 2 && seq.length <= 8);
        boolean seenBlank = false;
        for (boolean live : seq) {
            if (!live) seenBlank = true;
            else assertTrue(!seenBlank, "실탄이 공포탄 뒤에 있다");
        }
        for (long u = 2; u <= 3; u++) {
            ArgumentCaptor<Object> other = ArgumentCaptor.forClass(Object.class);
            verify(sender, timeout(1000)).sendTo(eq(u), eq(PacketType.MULTI_LOAD_SHOTGUN), other.capture());
            assertArrayEquals(seq, ((LoadShotgun) other.getValue()).sequence());
            assertEquals(((LoadShotgun) first.getValue()).firstTurnSeat(), ((LoadShotgun) other.getValue()).firstTurnSeat());
        }
        int firstTurn = ((LoadShotgun) first.getValue()).firstTurnSeat();
        assertTrue(firstTurn >= 0 && firstTurn < 3);
    }

    @Test
    @DisplayName("선공: 처음 죽은 사람이 남아 있으면 그 자리, 아니면 남은 자리 중 무작위 (모든 자리가 나온다)")
    void pickFirstTurn() {
        java.util.Random random = new java.util.Random(1);
        assertEquals(2, MultiGameService.pickFirstTurn(new boolean[] {true, true, true, true}, 2, random));
        boolean[] seen = new boolean[4];
        boolean[] connected = {true, false, true, true};
        for (int i = 0; i < 200; i++) {
            int seat = MultiGameService.pickFirstTurn(connected, 1, random);
            assertTrue(connected[seat], "나간 자리가 선공이 됐다");
            seen[seat] = true;
        }
        assertTrue(seen[0] && seen[2] && seen[3]);
    }

    @Test
    @DisplayName("다음 차례는 자리 번호가 큰 쪽(끝이면 0)이고 나간 자리는 건너뛴다")
    void nextTurn() {
        assertEquals(1, MultiGameService.nextTurn(0, new boolean[] {true, true, true}));
        assertEquals(0, MultiGameService.nextTurn(2, new boolean[] {true, true, true}));
        assertEquals(3, MultiGameService.nextTurn(1, new boolean[] {true, true, false, true}));
        assertEquals(0, MultiGameService.nextTurn(3, new boolean[] {true, false, false, true}));
    }
}
