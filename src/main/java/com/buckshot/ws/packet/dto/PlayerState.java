package com.buckshot.ws.packet.dto;

public record PlayerState(long userId, String nickname, int hp, int maxHp, ItemSlot[] items, int handcuffTurns) {
}
