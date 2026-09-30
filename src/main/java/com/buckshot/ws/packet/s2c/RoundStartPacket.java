package com.buckshot.ws.packet.s2c;

public record RoundStartPacket(int round, int liveCount, int blankCount) {
}
