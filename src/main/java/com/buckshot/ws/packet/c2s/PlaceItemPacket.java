package com.buckshot.ws.packet.c2s;

/** 지급받은 아이템 중 다음 하나를 놓을 칸 (지급받은 순서대로 하나씩 보낸다). */
public record PlaceItemPacket(int slot) {
}
