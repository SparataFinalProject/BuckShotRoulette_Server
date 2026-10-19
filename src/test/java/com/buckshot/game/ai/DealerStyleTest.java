package com.buckshot.game.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import com.buckshot.streak.timeline.TimelineJson;
import com.buckshot.streak.timeline.TimelineRecorder;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 딜러 성격(신중형·공격형·도박형): 고르기, 성격별 차이, 도박형의 아끼기. */
class DealerStyleTest {

    private static final int MAX = 6;

    private static DealerObservation obs(int hp, int oppHp, int live, int blank, Shell known, Item... held) {
        Set<Item> items = EnumSet.noneOf(Item.class);
        items.addAll(List.of(held));
        return new DealerObservation(hp, oppHp, MAX, live, blank, known, false, false, items);
    }

    private static DealerAction decide(DealerStyle style, DealerObservation o) {
        return new HardDealerBrain(style, new Random(1)).decide(o);
    }

    @Test
    @DisplayName("연승 성격은 셋이고, 무작위로 고르면 셋 다 나온다")
    void pickCoversAll() {
        Random r = new Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) seen.add(DealerStyle.pick(r).id());
        assertEquals(Set.of("CAUTIOUS", "AGGRESSIVE", "GAMBLER"), seen);
    }

    @Test
    @DisplayName("쇠톱: 실탄 확률 50%에서 공격형·도박형은 쓰고, 신중형은 아낀다 (60% 이상만)")
    void sawThresholdDiffers() {
        DealerObservation half = obs(MAX, MAX, 2, 2, null, Item.SAW);
        assertEquals(DealerAction.use(Item.SAW), decide(DealerStyle.AGGRESSIVE, half));
        assertEquals(DealerAction.SHOOT_OPPONENT, decide(DealerStyle.CAUTIOUS, half));

        DealerObservation twoThirds = obs(MAX, MAX, 2, 1, null, Item.SAW);
        assertEquals(DealerAction.use(Item.SAW), decide(DealerStyle.CAUTIOUS, twoThirds));
    }

    @Test
    @DisplayName("공격형은 다쳐도 수갑이 먼저, 신중형은 담배가 먼저")
    void orderDiffers() {
        DealerObservation hurt = obs(4, MAX, 2, 2, Shell.LIVE, Item.CIGARETTE, Item.HANDCUFFS);
        assertEquals(DealerAction.use(Item.HANDCUFFS), decide(DealerStyle.AGGRESSIVE, hurt));
        assertEquals(DealerAction.use(Item.CIGARETTE), decide(DealerStyle.CAUTIOUS, hurt));
    }

    @Test
    @DisplayName("맥주: 실탄 확률 1/3에서 도박형만 마신다 (30~70%)")
    void beerRangeDiffers() {
        DealerObservation third = obs(MAX, MAX, 1, 2, null, Item.BEER);
        assertEquals(DealerAction.SHOOT_SELF, decide(DealerStyle.CAUTIOUS, third));
        assertEquals(DealerAction.SHOOT_SELF, decide(DealerStyle.AGGRESSIVE, third));
        // 도박형은 10% 아끼므로 여러 번 물어서 대부분 맥주인지 본다
        HardDealerBrain gambler = new HardDealerBrain(DealerStyle.GAMBLER, new Random(3));
        int beer = 0;
        for (int i = 0; i < 1000; i++) if (gambler.decide(third).equals(DealerAction.use(Item.BEER))) beer++;
        assertTrue(beer > 850 && beer < 950, "맥주 " + beer + "/1000 (약 90% 기대)");
    }

    @Test
    @DisplayName("아끼는 확률이 0인 성격은 같은 상황에서 항상 같은 행동")
    void noSkipIsDeterministic() {
        DealerObservation o = obs(MAX, MAX, 2, 2, null, Item.MAGNIFIER);
        HardDealerBrain cautious = new HardDealerBrain(DealerStyle.CAUTIOUS, new Random(5));
        for (int i = 0; i < 200; i++) assertEquals(DealerAction.use(Item.MAGNIFIER), cautious.decide(o));
    }

    @Test
    @DisplayName("성격은 타임라인에 남고, 옛 타임라인(성격 없음)도 그대로 읽힌다")
    void styleInTimeline() {
        TimelineRecorder rec = new TimelineRecorder();
        rec.dealer(DealerStyle.GAMBLER.id());
        String json = TimelineJson.write(rec.build());
        assertTrue(json.contains("\"dealer\":\"GAMBLER\""), json);
        assertEquals("GAMBLER", TimelineJson.read(json).dealer());
        assertEquals(null, TimelineJson.read("{\"v\":1,\"rounds\":[]}").dealer());
    }

    @Test
    @DisplayName("성격마다 닉네임이 다르다 (기본 하드는 \"딜러\")")
    void nicknames() {
        Set<String> names = new HashSet<>();
        for (DealerStyle style : DealerStyle.STREAK) {
            assertTrue(!style.nickname().isBlank() && names.add(style.nickname()), style.id());
        }
        assertEquals("딜러", DealerStyle.HARD.nickname());
        assertTrue(!names.contains("딜러"), "연승 성격 닉네임은 기본 \"딜러\"와 달라야 한다");
    }
}
