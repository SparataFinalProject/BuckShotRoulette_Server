package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerResult;

public record GameOverPacket(long winnerUserId, String reason, PlayerResult[] results, boolean recorded) {
}
