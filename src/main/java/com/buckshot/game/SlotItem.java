package com.buckshot.game;

/**
 * 몇 번 칸에 어떤 아이템이 있는지 (ITEMS_GRANTED, GAME_START 패킷용).
 */
public record SlotItem(int slot, Item item) {
}
