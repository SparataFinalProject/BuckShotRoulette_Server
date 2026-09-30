package com.buckshot.ws.packet;

public final class PacketType {

    public static final String TEST = "TEST";

    // C2S
    public static final String MATCH_JOIN = "MATCH_JOIN";
    public static final String MATCH_CANCEL = "MATCH_CANCEL";
    public static final String GAME_READY = "GAME_READY";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String FIRE = "FIRE";

    // S2C
    public static final String MATCH_QUEUED = "MATCH_QUEUED";
    public static final String MATCH_CANCELLED = "MATCH_CANCELLED";
    public static final String MATCH_FOUND = "MATCH_FOUND";
    public static final String GAME_START = "GAME_START";
    public static final String ROUND_START = "ROUND_START";
    public static final String ITEMS_GRANTED = "ITEMS_GRANTED";
    public static final String TURN_START = "TURN_START";
    public static final String ITEM_USED = "ITEM_USED";
    public static final String SHELL_REVEALED = "SHELL_REVEALED";
    public static final String FIRE_RESULT = "FIRE_RESULT";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String ERROR = "ERROR";

    private PacketType() {
    }
}
