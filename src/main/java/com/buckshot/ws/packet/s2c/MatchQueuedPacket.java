package com.buckshot.ws.packet.s2c;

/** opponentSkin: 내 화면에서 상대 머리에 씌울 마스크 번호 (기다리는 동안에도 미리 보여 준다) */
public record MatchQueuedPacket(long queuedAt, int timeoutSec, int opponentSkin) {
}
