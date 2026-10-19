package com.buckshot.game;

/**
 * 아이템 종류. {@code name()}이 패킷 문자열 상수(ItemType)와 같다.
 */
public enum Item {
    HANDCUFFS,
    BEER,
    MAGNIFIER,
    CIGARETTE,
    SAW,
    // 새 아이템 (원작 Double or Nothing). 1대1 방(DevGameRoom)이 효과를 안다. 핵심 규칙(ItemEffects)은 아직 원작 5종만
    INVERTER,
    EXPIRED_MEDICINE,
    BURNER_PHONE,
    GAMBLE_COIN,
    ADRENALINE
}
