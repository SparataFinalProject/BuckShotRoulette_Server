package com.buckshot.game;

import com.buckshot.ws.packet.ItemType;

import java.util.List;

public final class ItemPools {

    public static final List<String> CLASSIC = List.of(
            ItemType.MAGNIFIER, ItemType.BEER, ItemType.CIGARETTE, ItemType.SAW, ItemType.HANDCUFFS);

    public static final List<String> DUEL = List.of(
            ItemType.MAGNIFIER, ItemType.BEER, ItemType.CIGARETTE, ItemType.SAW, ItemType.HANDCUFFS,
            ItemType.INVERTER, ItemType.EXPIRED_MEDICINE, ItemType.BURNER_PHONE, ItemType.GAMBLE_COIN, ItemType.ADRENALINE);

    public static final List<String> ALL = DUEL;

    private ItemPools() {
    }

    public static List<String> validate(List<String> pool) {
        if (pool == null || pool.isEmpty()) throw new IllegalArgumentException("item pool is empty");
        for (String item : pool) {
            if (!ALL.contains(item)) throw new IllegalArgumentException("unknown item in pool: " + item);
        }
        return List.copyOf(pool);
    }
}
