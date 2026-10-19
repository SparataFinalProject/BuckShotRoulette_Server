package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.buckshot.game.GameRules;
import com.buckshot.game.ItemPools;
import com.buckshot.game.ai.DealerTuning;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.service.StreakService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.Target;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 새 아이템까지 지급하는 방에서 딜러 AI가 막히지 않고 새 아이템을 실제로 쓰는지 (연승 목록은 아직 원작 5종). */
class DevStreakNewItemsTest {

    private static final long USER = 1L;
    private static final long DEALER = DevStreakGame.DEALER_ID;

    @Test
    @DisplayName("시즌 3개 × 성격 3개 × 20판: 모두 막히지 않고 끝난다")
    void everyStyleFinishes() {
        PacketSender sender = new PacketSender(null, null) {
            @Override
            public void sendTo(long to, String type, Object data) { }
        };
        for (com.buckshot.streak.StreakSeason season : com.buckshot.streak.StreakSeason.values())
        for (com.buckshot.game.ai.DealerStyle style : com.buckshot.game.ai.DealerStyle.STREAK) {
            for (long seed = 1; seed <= 20; seed++) {
                StreakService streakService = mock(StreakService.class);
                when(streakService.onGameEnd(anyLong(), any(), any(), any())).thenReturn(new StreakResult(0, 0, false, 0));
                Deque<Runnable> tasks = new ArrayDeque<>();
                var brain = new com.buckshot.game.ai.HardDealerBrain(style, new Random(seed));
                DevStreakGame game = new DevStreakGame(USER, streakService, (task, delayMs) -> tasks.add(task), brain) {
                    @Override
                    public List<String> itemPool() {
                        return season.items();
                    }
                };
                DevGameRoom room = new DevGameRoom(sender, USER, DEALER, game, new Random(seed));
                game.attach(room);
                Random human = new Random(seed * 31 + 7);
                room.begin();
                for (int step = 0; step < 10_000 && !room.isOver(); step++) {
                    if (!tasks.isEmpty()) {
                        tasks.poll().run();
                        continue;
                    }
                    if (room.turnUserId() == 0) {
                        room.placeAllFor(USER);
                        continue;
                    }
                    int slot = human.nextInt(room.itemsOf(USER).length);
                    if (human.nextInt(100) < 45 && room.useItem(USER, slot)) continue;
                    room.fire(USER, human.nextBoolean() ? Target.OPPONENT : Target.SELF);
                }
                assertTrue(room.isOver(), season + " " + style.id() + " seed " + seed);
            }
        }
    }

    @Test
    @DisplayName("새 아이템 포함 60판: 모두 끝나고, 딜러가 인버터·상한 약·대포폰을 각각 쓴다")
    void dealerUsesNewItems() {
        Map<String, Integer> dealerUsed = new HashMap<>();
        PacketSender sender = new PacketSender(null, null) {
            @Override
            public void sendTo(long to, String type, Object data) {
                if (to == USER && PacketType.ITEM_USED.equals(type) && data instanceof DevGameRoom.DevItemUsedPacket p
                        && p.userId() == DEALER) {
                    dealerUsed.merge(p.itemType(), 1, Integer::sum);
                }
            }
        };
        for (long seed = 1; seed <= 60; seed++) {
            StreakService streakService = mock(StreakService.class);
            when(streakService.onGameEnd(anyLong(), any(), any(), any())).thenReturn(new StreakResult(0, 0, false, 0));
            Deque<Runnable> tasks = new ArrayDeque<>();
            DevStreakGame game = new DevStreakGame(USER, streakService, (task, delayMs) -> tasks.add(task)) {
                @Override
                public List<String> itemPool() {
                    return com.buckshot.streak.StreakSeason.SEASON_3.items();
                }
            };
            DevGameRoom room = new DevGameRoom(sender, USER, DEALER, game, new Random(seed));
            game.attach(room);
            Random human = new Random(seed * 31 + 7);
            room.begin();
            for (int step = 0; step < 10_000 && !room.isOver(); step++) {
                if (!tasks.isEmpty()) {
                    tasks.poll().run();
                    continue;
                }
                if (room.turnUserId() == 0) {
                    room.placeAllFor(USER);
                    continue;
                }
                String[] items = room.itemsOf(USER);
                int slot = human.nextInt(items.length);
                if (human.nextInt(100) < 45 && room.useItem(USER, slot)) continue;
                room.fire(USER, human.nextBoolean() ? Target.OPPONENT : Target.SELF);
            }
            assertTrue(room.isOver(), "seed " + seed + " 판이 끝나지 않았다 (딜러가 멈춤?)");
        }
        // 상한 약은 hp가 최대보다 2 이상 낮고 3 이상일 때만 쓴다: 최대 hp가 5보다 작으면(임시 체력 3 등) 쓸 상황이 없다
        boolean medicinePossible = GameRules.STREAK_HP - 2 >= DealerTuning.HARD.medicineMinHp();   // 연승 목숨 4: hp 2인데 기준 3이라 하드는 쓰지 않는다
        List<String> expected = medicinePossible
                ? List.of(ItemType.INVERTER, ItemType.EXPIRED_MEDICINE, ItemType.BURNER_PHONE)
                : List.of(ItemType.INVERTER, ItemType.BURNER_PHONE);
        for (String item : expected) {
            assertTrue(dealerUsed.getOrDefault(item, 0) > 0, "딜러가 " + item + "을 한 번도 안 썼다 " + dealerUsed);
        }
    }
}
