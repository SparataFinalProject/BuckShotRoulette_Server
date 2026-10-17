package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buckshot.matchmaking.QuickMatchGame;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.s2c.ItemsGrantedPacket;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 1대1 일반 규칙: 아드레날린(상대 칸을 훔쳐 바로 쓴다), 아이템별 최대 개수, 매번 4개. */
class DevGameRoomAdrenalineTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private final PacketSender sender = mock(PacketSender.class);
    private final DevGameRoom room = new DevGameRoom(sender, A, B, new QuickMatchGame(A, B, null), new Random(11));

    private long start() {
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

    private DevGameRoom.DevItemUsedPacket lastItemUsed(long to) {
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendTo(eq(to), eq(PacketType.ITEM_USED), captor.capture());
        List<Object> all = captor.getAllValues();
        return (DevGameRoom.DevItemUsedPacket) all.get(all.size() - 1);
    }

    @Test
    @DisplayName("아드레날린을 쓰면 아드레날린 ITEM_USED, 이어서 STEAL_ITEM으로 고른 상대 아이템을 훔친 사람이 바로 쓴다 (fromUserId = 빼앗긴 사람)")
    void stealsAndUsesOpponentItem() throws Exception {
        long thief = start();
        long victim = thief == A ? B : A;
        give(thief, 0, ItemType.ADRENALINE);
        give(victim, 3, ItemType.MAGNIFIER);

        assertTrue(room.useItem(thief, 0));
        var used = lastItemUsed(victim);
        assertEquals(ItemType.ADRENALINE, used.itemType());
        assertEquals(0L, used.fromUserId());
        assertNull(room.itemsOf(thief)[0]);

        assertTrue(room.stealItem(thief, 3));
        var stolen = lastItemUsed(victim);
        assertEquals(thief, stolen.userId());
        assertEquals(ItemType.MAGNIFIER, stolen.itemType());
        assertEquals(3, stolen.slot());   // 빼앗긴 사람의 칸
        assertEquals(victim, stolen.fromUserId());
        assertNull(room.itemsOf(victim)[3]);
        verify(sender).sendTo(eq(thief), eq(PacketType.SHELL_REVEALED), any());   // 돋보기 효과는 훔친 사람에게
        verify(sender, never()).sendTo(eq(victim), eq(PacketType.SHELL_REVEALED), any());
    }

    @Test
    @DisplayName("훔친 담배는 훔친 사람의 체력을 채운다")
    void stolenCigaretteHealsThief() throws Exception {
        long thief = start();
        long victim = thief == A ? B : A;
        room.testOptions(null);
        give(thief, 0, ItemType.ADRENALINE);
        give(victim, 5, ItemType.CIGARETTE);
        var hpField = DevGameRoom.class.getDeclaredField("hp");
        hpField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var hp = (java.util.Map<Long, Integer>) hpField.get(room);
        hp.put(thief, DevGameRoom.HP - 1);

        room.useItem(thief, 0);
        assertTrue(room.stealItem(thief, 5));

        assertEquals(DevGameRoom.HP, room.hpOf(thief));
        assertEquals(DevGameRoom.HP, lastItemUsed(thief).hp());
    }

    @Test
    @DisplayName("아드레날린은 훔칠 수 없고, 아드레날린을 쓰지 않았으면 STEAL_ITEM을 거절한다")
    void cannotStealAdrenalineOrWithoutUsing() throws Exception {
        long thief = start();
        long victim = thief == A ? B : A;
        give(victim, 1, ItemType.BEER);
        assertFalse(room.stealItem(thief, 1), "아드레날린을 쓰지 않았다");
        assertEquals(ItemType.BEER, room.itemsOf(victim)[1]);

        give(thief, 0, ItemType.ADRENALINE);
        give(victim, 2, ItemType.ADRENALINE);
        room.useItem(thief, 0);
        assertFalse(room.stealItem(thief, 2), "아드레날린은 훔칠 수 없다");
        assertFalse(room.stealItem(thief, 7), "빈 칸");
        assertTrue(room.stealItem(thief, 1), "고르기는 아직 이어진다");
    }

    @Test
    @DisplayName("훔칠 게 없으면 아드레날린을 쓸 수 없고, 고르는 중 다른 행동을 하면 고르기가 끝난다")
    void needsStealableAndEndsOnOtherAction() throws Exception {
        long thief = start();
        long victim = thief == A ? B : A;
        give(thief, 0, ItemType.ADRENALINE);
        give(victim, 4, ItemType.ADRENALINE);
        assertFalse(room.useItem(thief, 0), "상대에게 아드레날린밖에 없다");
        assertEquals(ItemType.ADRENALINE, room.itemsOf(thief)[0]);

        give(victim, 6, ItemType.SAW);
        give(thief, 1, ItemType.MAGNIFIER);
        assertTrue(room.useItem(thief, 0));
        assertTrue(room.useItem(thief, 1), "훔치지 않고 다른 아이템을 쓴다");
        assertFalse(room.stealItem(thief, 6), "다른 행동을 했으니 고르기는 끝났다");
        assertEquals(ItemType.SAW, room.itemsOf(victim)[6]);
    }

    @Test
    @DisplayName("아이템별 최대 개수는 싱글 새 규칙과 같다")
    void maxHeldMatchesSingle() {
        assertEquals(8, DevGameRoom.maxHeld(ItemType.SAW));
        assertEquals(8, DevGameRoom.maxHeld(ItemType.MAGNIFIER));
        assertEquals(8, DevGameRoom.maxHeld(ItemType.BEER));
        assertEquals(8, DevGameRoom.maxHeld(ItemType.HANDCUFFS));
        assertEquals(8, DevGameRoom.maxHeld(ItemType.INVERTER));
        assertEquals(2, DevGameRoom.maxHeld(ItemType.CIGARETTE));
        assertEquals(2, DevGameRoom.maxHeld(ItemType.ADRENALINE));
        assertEquals(1, DevGameRoom.maxHeld(ItemType.EXPIRED_MEDICINE));
        assertEquals(1, DevGameRoom.maxHeld(ItemType.BURNER_PHONE));
        assertEquals(1, DevGameRoom.maxHeld(ItemType.GAMBLE_COIN));
    }

    @Test
    @DisplayName("이미 최대 개수만큼 가진 아이템은 새로 받지 않는다 (여러 번 지급해도)")
    void grantRespectsMaxHeld() throws Exception {
        var grant = DevGameRoom.class.getDeclaredMethod("grant", long.class, int.class);
        grant.setAccessible(true);
        for (int trial = 0; trial < 40; trial++) {
            var s = mock(PacketSender.class);
            var r = new DevGameRoom(s, A, B, new QuickMatchGame(A, B, null), new Random(trial));
            var field = DevGameRoom.class.getDeclaredField("items");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<Long, String[]>) field.get(r);
            String[] mine = map.get(A);
            mine[0] = ItemType.CIGARETTE;
            mine[1] = ItemType.CIGARETTE;
            mine[2] = ItemType.ADRENALINE;
            mine[3] = ItemType.ADRENALINE;
            mine[4] = ItemType.EXPIRED_MEDICINE;
            synchronized (r) {
                grant.invoke(r, A, 3);
            }
            var captor = ArgumentCaptor.forClass(Object.class);
            verify(s).sendTo(eq(A), eq(PacketType.ITEMS_GRANTED), captor.capture());
            for (var item : ((ItemsGrantedPacket) captor.getValue()).items()) {
                assertFalse(List.of(ItemType.CIGARETTE, ItemType.ADRENALINE, ItemType.EXPIRED_MEDICINE).contains(item.itemType()),
                        "이미 상한만큼 가진 아이템이 나왔다: " + item.itemType());
            }
        }
    }

    @Test
    @DisplayName("사람끼리 1대1은 매번 4개씩 받는다")
    void oneOnOneGrantsFour() {
        var game = new QuickMatchGame(A, B, null);
        for (int i = 0; i < 5; i++) assertEquals(4, game.itemsPerGrant(i, new Random()));
    }
}
