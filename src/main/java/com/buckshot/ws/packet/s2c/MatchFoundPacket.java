package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerProfile;

public record MatchFoundPacket(String gameId, PlayerProfile opponent, int readyTimeoutSec) {
}
