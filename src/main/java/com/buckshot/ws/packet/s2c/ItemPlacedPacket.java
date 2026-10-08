package com.buckshot.ws.packet.s2c;

/**
 * 아이템이 칸에 놓였다. index는 ITEMS_GRANTED.items 안의 순번.
 * 플레이어가 고른 것(auto false)은 상대에게만, 제한 시간이 지나 서버가 대신 놓은 것(auto true)은 둘 다에게 보낸다.
 */
public record ItemPlacedPacket(long userId, int index, int slot, String itemType, boolean auto) {
}
