package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.ItemSlot;

public record ItemsGrantedPacket(long userId, ItemSlot[] items, int discardedCount) {
}
