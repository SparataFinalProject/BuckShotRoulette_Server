package com.buckshot.ranking.dto;

import com.buckshot.game.Item;
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

    /** 아이템 5종을 모두 채운다. 저장은 쓴 것만 했으므로 안 쓴 아이템은 0. */
    public static Map<Item, Integer> allItems(Map<Item, Integer> used) {
        Map<Item, Integer> all = new EnumMap<>(Item.class);
        for (Item item : Item.values()) {
            all.put(item, used.getOrDefault(item, 0));
        }
        return all;
    }
}
