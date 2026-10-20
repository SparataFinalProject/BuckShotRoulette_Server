package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.TestOptions;
import com.buckshot.ws.packet.s2c.ItemsGrantedPacket;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 개발용 테스트 옵션: 첫 장전부터 아이템을 주고, 지급 후보와 개수를 고정한다. */
class DevGameRoomTestOptionsTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private final PacketSender sender = mock(PacketSender.class);

    private DevGameRoom room(TestOptions options) {
        var room = new DevGameRoom(sender, A, B, DevGameRoom.Listener.NONE, new Random(3));
        room.testOptions(options);
        return room;
    }

    @Test
    @DisplayName("옵션이 없으면 첫 장전에는 아이템을 주지 않는다")
    void noItemsOnFirstLoadByDefault() {
        room(null).begin();

        verify(sender, never()).sendTo(any(Long.class), eq(PacketType.ITEMS_GRANTED), any());
        verify(sender).sendTo(eq(A), eq(PacketType.ROUND_START), any());
    }

    @Test
    @DisplayName("첫 턴 아이템 + 아이템 고정 + 개수 고정: 둘 다에게 고른 아이템만 그 개수로 주고, 도박 동전·담배 상한도 무시한다")
    void givesFixedItemsOnFirstTurn() {
        room(new TestOptions(true, true, List.of(ItemType.GAMBLE_COIN), 3)).begin();

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.times(2)).sendTo(eq(A), eq(PacketType.ITEMS_GRANTED), captor.capture());
        for (Object o : captor.getAllValues()) {
            var granted = (ItemsGrantedPacket) o;
            assertEquals(3, granted.items().length);
            for (var item : granted.items()) assertEquals(ItemType.GAMBLE_COIN, item.itemType());   // 1개 상한을 넘겨도 고정한 대로
        }
        verify(sender, never()).sendTo(eq(A), eq(PacketType.ROUND_START), any());   // 아이템을 다 놓은 뒤에 장전한다
    }

    @Test
    @DisplayName("꺼져 있는 옵션이나 알 수 없는 아이템 이름은 무시하고 일반 규칙을 쓴다")
    void ignoresDisabledOrUnknown() {
        room(new TestOptions(false, true, List.of(ItemType.BEER), 5)).begin();
        verify(sender, never()).sendTo(any(Long.class), eq(PacketType.ITEMS_GRANTED), any());

        var other = mock(PacketSender.class);
        var room = new DevGameRoom(other, A, B, DevGameRoom.Listener.NONE, new Random(3));
        room.testOptions(new TestOptions(true, true, List.of("NOT_AN_ITEM"), 0));
        room.begin();
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(other, org.mockito.Mockito.times(2)).sendTo(eq(A), eq(PacketType.ITEMS_GRANTED), captor.capture());   // 두 플레이어 각각의 지급이 A에게 온다
        assertTrue(((ItemsGrantedPacket) captor.getValue()).items().length > 0);   // 첫 턴 아이템은 켜져 있으니 받는다 (일반 후보에서)
    }

    @Test
    @DisplayName("시작 체력 옵션: 두 사람 모두 그 체력으로 시작한다")
    void startHpSetsBothPlayers() {
        var room = room(new TestOptions(true, false, List.of(), 0, 2));

        assertEquals(2, room.hpOf(A));
        assertEquals(2, room.hpOf(B));
    }

    @Test
    @DisplayName("시작 체력이 0이거나 옵션이 꺼져 있으면 원래 체력, 기본 최대 체력을 넘으면 그 체력으로 맞춘다")
    void startHpIsOptionalAndCapped() {
        assertEquals(DevGameRoom.HP, room(null).hpOf(A));
        assertEquals(DevGameRoom.HP, room(new TestOptions(true, false, List.of(), 0, 0)).hpOf(A));
        assertEquals(DevGameRoom.HP, room(new TestOptions(false, false, List.of(), 0, 1)).hpOf(A), "꺼진 옵션은 무시");
        assertEquals(DevGameRoom.HP, room(new TestOptions(true, false, List.of(), 0, 99)).hpOf(A), "기본 최대 체력을 넘으면 맞춘다");
        assertEquals(1, room(new TestOptions(true, false, List.of(), 0, 1)).hpOf(B));
    }

    @Test
    @DisplayName("GAME_START용 시작 체력 계산: 옵션이 정했으면 그 값, 아니면 기본값")
    void startHpHelper() {
        assertEquals(6, DevGameRoom.startHp(null, 6));
        assertEquals(6, DevGameRoom.startHp(new TestOptions(true, false, List.of(), 0, 0), 6));
        assertEquals(3, DevGameRoom.startHp(new TestOptions(true, false, List.of(), 0, 3), 6));
        assertEquals(6, DevGameRoom.startHp(new TestOptions(false, false, List.of(), 0, 3), 6));
    }

    @Test
    @DisplayName("시작 체력이 1이면 실탄 한 발로 끝난다 (체력 1이 최대 체력이기도 해서 담배로 회복도 안 된다)")
    void startHpOneIsAlsoMax() {
        var room = room(new TestOptions(true, false, List.of(), 0, 1));

        assertEquals(1, room.hpOf(A));
        assertEquals(1, room.hpOf(B));
    }
}
