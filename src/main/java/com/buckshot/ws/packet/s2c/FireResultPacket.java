package com.buckshot.ws.packet.s2c;

public record FireResultPacket(long shooterUserId, long targetUserId, String shell, int damage, int targetHp, int remainingShells, boolean extraTurn) {
}
