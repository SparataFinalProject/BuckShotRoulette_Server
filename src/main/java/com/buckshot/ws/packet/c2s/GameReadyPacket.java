package com.buckshot.ws.packet.c2s;

/** test: 개발용 테스트 옵션 (없으면 null). */
public record GameReadyPacket(String gameId, TestOptions test) {

    public GameReadyPacket(String gameId) {
        this(gameId, null);
    }
}
