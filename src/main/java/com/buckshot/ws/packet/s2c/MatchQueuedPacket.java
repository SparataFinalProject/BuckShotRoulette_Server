package com.buckshot.ws.packet.s2c;

public record MatchQueuedPacket(long queuedAt, int timeoutSec) {
}
