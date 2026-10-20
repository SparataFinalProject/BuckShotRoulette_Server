package com.buckshot.streak;

import com.buckshot.game.ItemPools;
import com.buckshot.ws.packet.ItemType;
import java.util.List;

public enum StreakSeason {

    SEASON_1(1, "원작", ItemPools.CLASSIC),

    SEASON_2(2, "스파르타", List.of(
            ItemType.MAGNIFIER, ItemType.BURNER_PHONE, ItemType.INVERTER, ItemType.SAW, ItemType.BEER, ItemType.HANDCUFFS)),

    // 1대1 목록에서 도박 동전·아드레날린은 뺐다 (딜러 AI가 아직 쓰지 못하고, 동전은 칸을 다시 뽑아 타임라인이 따라가지 못한다)
    SEASON_3(3, "멀티룰", List.of(
            ItemType.MAGNIFIER, ItemType.BEER, ItemType.CIGARETTE, ItemType.SAW, ItemType.HANDCUFFS,
            ItemType.INVERTER, ItemType.EXPIRED_MEDICINE, ItemType.BURNER_PHONE));

    public static final StreakSeason CURRENT = SEASON_2;

    private final int number;
    private final String title;
    private final List<String> items;

    StreakSeason(int number, String title, List<String> items) {
        this.number = number;
        this.title = title;
        this.items = ItemPools.validate(items);
    }

    public int number() {
        return number;
    }

    public String title() {
        return title;
    }

    public List<String> items() {
        return items;
    }

    public static StreakSeason of(int number) {
        for (StreakSeason s : values()) if (s.number == number) return s;
        return SEASON_1;
    }
}
