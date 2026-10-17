package com.buckshot.ws.packet;

public final class PacketType {

    public static final String TEST = "TEST";

    // C2S
    public static final String MATCH_JOIN = "MATCH_JOIN";
    public static final String MATCH_CANCEL = "MATCH_CANCEL";
    public static final String RANKED_START = "RANKED_START";   // 연승 모드: 딜러와 바로 한 판 (대기열 없음)
    public static final String GAME_READY = "GAME_READY";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String STEAL_ITEM = "STEAL_ITEM";   // 1대1 아드레날린: 아드레날린을 쓴 뒤 훔칠 상대 칸 (서버가 ITEM_USED fromUserId = 빼앗긴 사람으로 알린다)
    public static final String FIRE = "FIRE";
    public static final String GUN_PICKUP = "GUN_PICKUP";   // 개발용: 총을 집었다 (상대에게 전달)
    public static final String AIM = "AIM";                  // 개발용: 대상을 골라 겨누기 시작했다 (상대에게 전달)
    public static final String PLACE_ITEM = "PLACE_ITEM";    // 개발용: 지급받은 아이템을 놓을 칸
    public static final String PLACE_READY = "PLACE_READY";  // 개발용: 아이템 상자를 열었다 (이때부터 배치 제한 시간을 센다)
    public static final String ROOM_CREATE = "ROOM_CREATE";              // 개발용 방: 방 만들기
    public static final String ROOM_JOIN = "ROOM_JOIN";                  // 개발용 방: 목록(roomId) 또는 방 코드(code)로 참가
    public static final String ROOM_LEAVE = "ROOM_LEAVE";                // 개발용 방: 방 떠나기
    public static final String ROOM_SET_FRIENDS_ONLY = "ROOM_SET_FRIENDS_ONLY";   // 개발용 방: 친구 전용(목록에서 숨김) 켜기/끄기, 방장만
    public static final String ROOM_SET_ROUNDS = "ROOM_SET_ROUNDS";    // 개발용 방: 3~4인 라운드 수(1~3) 정하기, 방장만
    public static final String ROOM_SET_FOUR_PLAYER = "ROOM_SET_FOUR_PLAYER";    // 개발용 방: 3~4인 전용 켜기/끄기, 방장만 (3명 이상이면 끌 수 없음)
    public static final String ROOM_KICK = "ROOM_KICK";                  // 개발용 방: 추방, 방장만
    public static final String ROOM_START = "ROOM_START";                // 개발용 방: 게임 시작, 방장만 (기본 방은 2명일 때, 3~4인 전용 방은 3명 이상일 때)
    public static final String MULTI_READY = "MULTI_READY";              // 3~4인 전용 게임: 씬에 들어왔다
    public static final String MULTI_INTRO_DONE = "MULTI_INTRO_DONE";    // 3~4인 전용 게임: 입장 연출이 끝났다
    public static final String MULTI_GRAB_ITEM = "MULTI_GRAB_ITEM";      // 3~4인 전용 게임: 상자에서 아이템을 꺼낸다
    public static final String MULTI_CHAT = "MULTI_CHAT";                // 3~4인 전용 게임: 채팅 보내기 (text)
    public static final String MULTI_PLACE_ITEM = "MULTI_PLACE_ITEM";    // 3~4인 전용 게임: 꺼낸 아이템을 놓을 칸 (cell)
    public static final String MULTI_PICKUP_SHOTGUN = "MULTI_PICKUP_SHOTGUN";   // 3~4인 전용 게임: 내 차례에 총을 집는다 (원작 pickup shotgun request)
    public static final String MULTI_LOOK = "MULTI_LOOK";                // 3~4인 전용 게임: 총을 들고 대상을 고르는 동안 마우스가 향한 쪽 (direction: left/forward/right)
    public static final String MULTI_SHOOT = "MULTI_SHOOT";              // 3~4인 전용 게임: 대상을 쏜다 (target 자리)
    public static final String MULTI_USE_ITEM = "MULTI_USE_ITEM";        // 3~4인 전용 게임: 내 차례에 내 칸(cell)의 아이템을 쓴다
    public static final String MULTI_ITEM_SECONDARY = "MULTI_ITEM_SECONDARY";   // 3~4인 전용 게임: 재머 대상(target) / 아드레날린으로 훔칠 아이템(target 자리, cell)을 골랐다
    public static final String ROOM_LIST_WATCH = "ROOM_LIST_WATCH";      // 개발용 방: 방 목록 화면을 열고 닫음 (여는 동안 바뀔 때마다 ROOM_LIST)
    public static final String ROOM_STATE_REQUEST = "ROOM_STATE_REQUEST";   // 개발용 방: 로비에 들어와 지금 있는 방을 묻는다 (있으면 ROOM_STATE, 게임이 끝나 다시 열린 방)

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
    public static final String ROOM_START_MULTI = "ROOM_START_MULTI";    // 개발용 방: 3~4인 전용 방의 게임 시작 (참가자 모두에게, 매칭 없이 바로 멀티게임2 씬으로)
    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String MULTI_ROUND_START = "MULTI_ROUND_START";            // 3~4인 전용 게임: 라운드, 목숨, 차례 순서, 자리별 받을 아이템 개수
    public static final String MULTI_ITEM_PHASE_BEGIN = "MULTI_ITEM_PHASE_BEGIN";  // 3~4인 전용 게임: 모두 아이템 상자가 나온다
    public static final String MULTI_ITEM_GRABBED = "MULTI_ITEM_GRABBED";          // 3~4인 전용 게임: 누가 상자에서 아이템을 꺼냈다
    public static final String MULTI_ITEM_PLACED = "MULTI_ITEM_PLACED";            // 3~4인 전용 게임: 누가 아이템을 칸에 놓았다
    public static final String MULTI_CHAT_MSG = "MULTI_CHAT_MSG";                  // 3~4인 전용 게임: 누가 채팅을 보냈다 (seat, text)
    public static final String MULTI_ITEM_PHASE_DONE = "MULTI_ITEM_PHASE_DONE";    // 3~4인 전용 게임: 누구의 아이템 받기가 끝났다                  // 개발용 방: 목록에 보이는 방들
    public static final String MULTI_LOAD_SHOTGUN = "MULTI_LOAD_SHOTGUN";          // 3~4인 전용 게임: 셸 장전 (보이는 셸 순서)
    public static final String MULTI_TURN = "MULTI_TURN";                          // 3~4인 전용 게임: 이 자리가 차례 (첫 차례는 장전 패킷의 firstTurnSeat)
    public static final String MULTI_SHOTGUN_PICKED = "MULTI_SHOTGUN_PICKED";      // 3~4인 전용 게임: 누가 총을 집었다
    public static final String MULTI_LOOK_AT = "MULTI_LOOK_AT";                    // 3~4인 전용 게임: 총을 든 사람이 고르는 쪽을 본다 (보낸 사람 빼고)
    public static final String MULTI_SHOT = "MULTI_SHOT";                          // 3~4인 전용 게임: 누가 누구를 쐈다 (실탄 여부, 체력)
    public static final String MULTI_SHOTGUN_DROPPED = "MULTI_SHOTGUN_DROPPED";    // 3~4인 전용 게임: 대상 고르기 제한 시간이 지나 총을 내려놓았다
    public static final String MULTI_ITEM_USED = "MULTI_ITEM_USED";                // 3~4인 전용 게임: 누가 아이템을 썼다 (돋보기로 본 셸은 쓴 사람에게만)
    public static final String MULTI_ITEM_SECONDARY_DONE = "MULTI_ITEM_SECONDARY_DONE";   // 3~4인 전용 게임: 재머 결과(대상 또는 제한 시간), 아드레날린 제한 시간
    public static final String MULTI_ROUND_END = "MULTI_ROUND_END";                // 3~4인 전용 게임: 한 명만 남아 라운드가 끝났다
    public static final String MULTI_PLAYER_LEFT = "MULTI_PLAYER_LEFT";            // 3~4인 전용 게임: 누가 연결이 끊겨 나갔다 (자리는 비고 체력 0). 이어서 차례 넘김, 라운드 끝 등이 온다

    private PacketType() {
    }
}
