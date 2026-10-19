package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.GameRules;
import com.buckshot.game.ItemPools;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.Target;
import com.buckshot.ws.packet.dto.ItemSlot;
import com.buckshot.ws.packet.s2c.ItemsGrantedPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 방에 넘긴 아이템 목록(Listener.itemPool)에서만 지급되는지. */
class DevGameRoomItemPoolTest {

    private static final long A = 1L, B = 2L;

    /** 여러 판을 끝까지 돌리며 지급된 아이템과, 배치가 끝난 뒤 한 사람이 들고 있던 담배 수의 최댓값을 모은다. */
    record Granted(List<String> items, int maxCigarettesHeld, int grants) {
    }

    static Granted play(List<String> pool, int games) {
        List<String> items = new ArrayList<>();
        int[] grants = { 0 };
        int maxCig = 0;
        PacketSender sender = new PacketSender(null, null) {
            @Override
            public void sendTo(long to, String type, Object data) {
                if (to == A && PacketType.ITEMS_GRANTED.equals(type)) {   // 같은 패킷이 두 사람에게 가므로 한쪽만 센다
                    grants[0]++;
                    for (ItemSlot s : ((ItemsGrantedPacket) data).items()) items.add(s.itemType());
                }
            }
        };
        DevGameRoom.Listener listener = new DevGameRoom.Listener() {
            @Override
            public List<String> itemPool() {
                return pool;
            }
        };
        for (long seed = 1; seed <= games; seed++) {
            DevGameRoom room = new DevGameRoom(sender, A, B, listener, new Random(seed));
            Random r = new Random(seed * 7 + 1);
            room.begin();
            for (int step = 0; step < 10_000 && !room.isOver(); step++) {
                long turn = room.turnUserId();
                if (turn == 0) {                       // 배치: 둘 다 앞 칸부터 놓는다
                    room.placeAllFor(A);
                    room.placeAllFor(B);
                    continue;
                }
                for (long id : new long[] { A, B }) {
                    int cig = 0;
                    for (String s : room.itemsOf(id)) if (ItemType.CIGARETTE.equals(s)) cig++;
                    maxCig = Math.max(maxCig, cig);
                }
                room.fire(turn, r.nextBoolean() ? Target.OPPONENT : Target.SELF);   // 아이템은 쓰지 않아 칸이 쌓인다
            }
            assertTrue(room.isOver(), "seed " + seed);
        }
        return new Granted(items, maxCig, grants[0]);
    }

    @Test
    @DisplayName("고른 아이템만 지급된다 (쇠톱만, 새 아이템만)")
    void onlyPoolItems() {
        Granted sawOnly = play(List.of(ItemType.SAW), 30);
        assertTrue(sawOnly.grants() > 0, "지급이 한 번도 없었다");
        assertEquals(Set.of(ItemType.SAW), new HashSet<>(sawOnly.items()));

        List<String> newOnly = List.of(ItemType.INVERTER, ItemType.EXPIRED_MEDICINE, ItemType.BURNER_PHONE);
        Granted g = play(newOnly, 30);
        assertEquals(new HashSet<>(newOnly), new HashSet<>(g.items()), "세 종류가 모두, 그리고 그것만 나와야 한다");
    }

    @Test
    @DisplayName("기본 목록: 연승(기본)은 원작 5종, 바로 매칭(NONE)은 새 아이템 포함")
    void defaultPools() {
        assertEquals(ItemPools.CLASSIC, new DevGameRoom.Listener() { }.itemPool());
        assertEquals(ItemPools.DUEL, DevGameRoom.Listener.NONE.itemPool());

        Granted classic = play(ItemPools.CLASSIC, 30);
        assertEquals(new HashSet<>(ItemPools.CLASSIC), new HashSet<>(classic.items()));
    }

    @Test
    @DisplayName("담배만 있는 목록: 무한 반복 없이 끝까지 지급된다")
    void cigaretteOnlyRespectsCap() {
        Granted g = play(List.of(ItemType.CIGARETTE), 30);
        assertTrue(g.grants() > 0);
        assertEquals(Set.of(ItemType.CIGARETTE), new HashSet<>(g.items()));
        // 다 찬 아이템만 남으면 목록 전체에서 다시 뽑는다 (싱글 규칙, DevGameRoom.draw): 상한을 넘을 수 있지만 멈추지는 않는다
        assertFalse(g.items().isEmpty());
    }

    @Test
    @DisplayName("상한 약 실패로 체력이 0이 되면 바로 GAME_OVER(HP_ZERO), 이긴 쪽은 상대")
    void medicineCanEndGame() {
        DevGameRoom.Listener listener = new DevGameRoom.Listener() {
            @Override
            public List<String> itemPool() {
                return List.of(ItemType.EXPIRED_MEDICINE);
            }
        };
        int endedByMedicine = 0;
        for (long seed = 1; seed <= 40; seed++) {
            List<Object[]> sent = new ArrayList<>();
            PacketSender sender = new PacketSender(null, null) {
                @Override
                public void sendTo(long to, String type, Object data) {
                    if (to == A) sent.add(new Object[] { type, data });
                }
            };
            Random alwaysFail = new Random(seed) {
                @Override
                public double nextDouble() {
                    return 0.99;   // 상한 약은 항상 실패 (-1)
                }
            };
            DevGameRoom room = new DevGameRoom(sender, A, B, listener, alwaysFail);
            Random r = new Random(seed);
            room.begin();
            for (int step = 0; step < 10_000 && !room.isOver(); step++) {
                long turn = room.turnUserId();
                if (turn == 0) {
                    room.placeAllFor(A);
                    room.placeAllFor(B);
                    continue;
                }
                String[] items = room.itemsOf(turn);
                boolean used = false;
                for (int s = 0; s < items.length && !used; s++) if (items[s] != null) used = room.useItem(turn, s);
                if (!used) room.fire(turn, r.nextBoolean() ? Target.SELF : Target.OPPONENT);
            }
            assertTrue(room.isOver(), "seed " + seed);

            int last = sent.size() - 1;
            assertEquals(PacketType.GAME_OVER, sent.get(last)[0], "seed " + seed);
            if (sent.get(last - 1)[1] instanceof DevGameRoom.DevItemUsedPacket used) {   // 상한 약으로 끝난 판
                endedByMedicine++;
                assertEquals(0, used.hp(), "seed " + seed);
                var over = (com.buckshot.ws.packet.s2c.GameOverPacket) sent.get(last)[1];
                assertEquals(used.userId() == A ? B : A, over.winnerUserId(), "seed " + seed + " 승자");
                assertEquals(com.buckshot.ws.packet.GameOverReason.HP_ZERO, over.reason());
            }
        }
        assertTrue(endedByMedicine > 0, "상한 약으로 끝난 판이 한 번도 없었다");
    }

    @Test
    @DisplayName("빈 목록이나 방이 모르는 아이템은 방을 만들 때 거절")
    void invalidPool() {
        PacketSender sender = new PacketSender(null, null) {
            @Override
            public void sendTo(long to, String type, Object data) { }
        };
        for (List<String> bad : List.of(List.<String>of(), List.of("JAMMER"))) {
            DevGameRoom.Listener listener = new DevGameRoom.Listener() {
                @Override
                public List<String> itemPool() {
                    return bad;
                }
            };
            assertThrows(IllegalArgumentException.class, () -> new DevGameRoom(sender, A, B, listener), bad.toString());
        }
    }
}
