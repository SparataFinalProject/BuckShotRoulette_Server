package com.buckshot.ws.packet.s2c;

public record ItemUsedPacket(long userId, int slot, String itemType, int hp, int remainingShells, boolean sawActive, long handcuffedUserId, int handcuffTurns) {
}
