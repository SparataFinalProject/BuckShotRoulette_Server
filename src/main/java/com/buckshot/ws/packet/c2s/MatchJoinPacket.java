package com.buckshot.ws.packet.c2s;

/**
 * mode: "RANKED"면 레이팅으로 짝을 찾는 랭크 매칭. 비어 있거나 다른 값이면 기존 바로 매칭.
 */
public record MatchJoinPacket(String mode) {

    public static final String MODE_RANKED = "RANKED";

    public boolean ranked() {
        return MODE_RANKED.equalsIgnoreCase(mode);
    }
}
