package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerState;

public record GameStartPacket(String gameId, int maxItemSlots, PlayerState[] players) {
}
