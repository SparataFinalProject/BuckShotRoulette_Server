package com.buckshot.ws.packet;

public final class MatchCancelReason {

    public static final String USER_REQUEST = "USER_REQUEST";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String READY_TIMEOUT = "READY_TIMEOUT";
    public static final String OPPONENT_DISCONNECTED = "OPPONENT_DISCONNECTED";
    /** 방장이 게임 시작을 누른 순간 참가자가 방 나가기를 눌렀다 (서버가 게임 시작을 먼저 처리한 경우) */
    public static final String OPPONENT_LEFT = "OPPONENT_LEFT";

    private MatchCancelReason() {
    }
}
