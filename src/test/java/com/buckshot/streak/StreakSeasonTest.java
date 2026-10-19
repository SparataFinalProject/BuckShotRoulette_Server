package com.buckshot.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.Item;
import com.buckshot.ranking.dto.StatMath;
import com.buckshot.ws.packet.ItemType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakSeasonTest {

    @Test
    @DisplayName("시즌 번호는 겹치지 않고, 모르는 번호는 시즌 1")
    void numbers() {
        Set<Integer> seen = new HashSet<>();
        for (StreakSeason s : StreakSeason.values()) assertTrue(seen.add(s.number()), s.name());
        assertEquals(StreakSeason.SEASON_2, StreakSeason.of(2));
        assertEquals(StreakSeason.SEASON_1, StreakSeason.of(0));
        assertEquals(StreakSeason.SEASON_1, StreakSeason.of(99));
    }

    @Test
    @DisplayName("구성: 시즌 1 원작 5종, 시즌 2는 담배 없이 대포폰·인버터, 시즌 3은 8종 전부")
    void items() {
        assertEquals(5, StreakSeason.SEASON_1.items().size());
        assertFalse(StreakSeason.SEASON_1.items().contains(ItemType.INVERTER));

        assertFalse(StreakSeason.SEASON_2.items().contains(ItemType.CIGARETTE));
        assertTrue(StreakSeason.SEASON_2.items().containsAll(Set.of(ItemType.BURNER_PHONE, ItemType.INVERTER, ItemType.MAGNIFIER)));

        assertEquals(8, StreakSeason.SEASON_3.items().size());
        // 딜러 AI·기록이 아는 아이템인지 (game.Item에 있어야 사용 횟수를 센다)
        for (StreakSeason s : StreakSeason.values()) for (String type : s.items()) Item.valueOf(type);
    }

    @Test
    @DisplayName("판 상세의 아이템 칸은 그 시즌 구성대로 채운다 (안 쓴 것은 0)")
    void statColumnsFollowSeason() {
        Map<Item, Integer> s2 = StatMath.allItems(2, Map.of(Item.INVERTER, 1));
        assertEquals(6, s2.size());
        assertEquals(1, s2.get(Item.INVERTER));
        assertEquals(0, s2.get(Item.BURNER_PHONE));
        assertFalse(s2.containsKey(Item.CIGARETTE));
        assertEquals(5, StatMath.allItems(1, Map.of()).size());
    }
}
