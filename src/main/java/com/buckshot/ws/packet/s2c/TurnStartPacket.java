package com.buckshot.ws.packet.s2c;

public record TurnStartPacket(long turnUserId, int turnNumber, long skippedUserId, int handcuffTurns) {
}
