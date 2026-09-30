package com.buckshot.ws.packet;

public final class MatchCancelReason {

    public static final String USER_REQUEST = "USER_REQUEST";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String READY_TIMEOUT = "READY_TIMEOUT";
    public static final String OPPONENT_DISCONNECTED = "OPPONENT_DISCONNECTED";

    private MatchCancelReason() {
    }
}
