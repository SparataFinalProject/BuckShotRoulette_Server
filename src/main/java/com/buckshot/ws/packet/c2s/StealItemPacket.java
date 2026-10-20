package com.buckshot.ws.packet.c2s;

/** 1대1 아드레날린: 훔칠 상대 칸 (상대가 놓은 칸 번호 그대로). */
public record StealItemPacket(int slot) {
}
