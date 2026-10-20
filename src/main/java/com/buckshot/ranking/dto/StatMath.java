package com.buckshot.ranking.dto;

import com.buckshot.game.Item;
import com.buckshot.streak.StreakSeason;
import java.util.EnumMap;
import java.util.Map;

/** 응답을 만들 때 쓰는 계산 (비율, 아이템 칸 채우기). 저장하지 않고 보여 줄 때만 계산한다. */
public final class StatMath {

    private StatMath() {
    }

    /** 0으로 나누지 않는다 (분모가 0이면 0). */
    public static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    /** 백분율 (0~100). 분모가 0이면 0. */
    public static double percent(long part, long whole) {
        return ratio(part * 100, whole);
    }

    /** 그 시즌에 나오는 아이템(StreakSeason.items)을 모두 채운다. 저장은 쓴 것만 했으므로 안 쓴 아이템은 0. */
    public static Map<Item, Integer> allItems(int season, Map<Item, Integer> used) {
        Map<Item, Integer> all = new EnumMap<>(Item.class);
        for (String type : StreakSeason.of(season).items()) {
            Item item = Item.valueOf(type);
            all.put(item, used.getOrDefault(item, 0));
        }
        used.forEach((item, n) -> all.putIfAbsent(item, n));   // 목록 밖이라도 쓴 기록이 있으면 그대로 보여 준다
        return all;
    }
}
