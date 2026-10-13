package com.buckshot.ws.packet;

public final class PacketType {

    public static final String TEST = "TEST";

    // C2S
    public static final String MATCH_JOIN = "MATCH_JOIN";
    public static final String MATCH_CANCEL = "MATCH_CANCEL";
    public static final String GAME_READY = "GAME_READY";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String FIRE = "FIRE";
    public static final String GUN_PICKUP = "GUN_PICKUP";   // 개발용: 총을 집었다 (상대에게 전달)
    public static final String AIM = "AIM";                  // 개발용: 대상을 골라 겨누기 시작했다 (상대에게 전달)
    public static final String PLACE_ITEM = "PLACE_ITEM";    // 개발용: 지급받은 아이템을 놓을 칸
    public static final String ROOM_CREATE = "ROOM_CREATE";              // 개발용 방: 방 만들기
    public static final String ROOM_JOIN = "ROOM_JOIN";                  // 개발용 방: 목록(roomId) 또는 방 코드(code)로 참가
    public static final String ROOM_LEAVE = "ROOM_LEAVE";                // 개발용 방: 방 떠나기
    public static final String ROOM_SET_FRIENDS_ONLY = "ROOM_SET_FRIENDS_ONLY";   // 개발용 방: 친구 전용(목록에서 숨김) 켜기/끄기, 방장만
    public static final String ROOM_KICK = "ROOM_KICK";                  // 개발용 방: 추방, 방장만
    public static final String ROOM_START = "ROOM_START";                // 개발용 방: 게임 시작, 방장만 (2명일 때)
    public static final String ROOM_LIST_WATCH = "ROOM_LIST_WATCH";      // 개발용 방: 방 목록 화면을 열고 닫음 (여는 동안 바뀔 때마다 ROOM_LIST)

    // S2C
    public static final String MATCH_QUEUED = "MATCH_QUEUED";
    public static final String MATCH_CANCELLED = "MATCH_CANCELLED";
    public static final String MATCH_FOUND = "MATCH_FOUND";
    public static final String GAME_START = "GAME_START";
    public static final String ROUND_START = "ROUND_START";
    public static final String ITEMS_GRANTED = "ITEMS_GRANTED";
    public static final String ITEM_PLACED = "ITEM_PLACED";
    public static final String TURN_START = "TURN_START";
    public static final String ITEM_USED = "ITEM_USED";
    public static final String SHELL_REVEALED = "SHELL_REVEALED";
    public static final String FIRE_RESULT = "FIRE_RESULT";
    public static final String OPPONENT_GUN_PICKUP = "OPPONENT_GUN_PICKUP";
    public static final String OPPONENT_AIM = "OPPONENT_AIM";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String ERROR = "ERROR";
    public static final String ROOM_STATE = "ROOM_STATE";                // 개발용 방: 내가 있는 방의 현재 상태
    public static final String ROOM_KICKED = "ROOM_KICKED";              // 개발용 방: 방장에게 추방당함
    public static final String ROOM_LEFT = "ROOM_LEFT";                  // 개발용 방: ROOM_LEAVE 처리 끝 (이후로는 그 방 상태를 보내지 않음)
    public static final String ROOM_JOIN_FAILED = "ROOM_JOIN_FAILED";    // 개발용 방: 참가 실패 (reason)
    public static final String ROOM_LIST = "ROOM_LIST";                  // 개발용 방: 목록에 보이는 방들

    private PacketType() {
    }
}
