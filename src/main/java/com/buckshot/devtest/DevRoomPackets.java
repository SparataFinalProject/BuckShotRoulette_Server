package com.buckshot.devtest;

import java.util.List;

/** 개발용 방 패킷 (DevRoomService 참고). 정식 방 설계가 생기면 ws/packet으로 옮긴다. */
public final class DevRoomPackets {

    // C2S
    public record Empty() {
    }

    /** 목록에서 고르면 roomId, 방 코드로 들어오면 code (둘 중 하나만 채운다) */
    public record Join(String roomId, String code) {
    }

    public record SetFriendsOnly(boolean friendsOnly) {
    }

    public record Kick(long userId) {
    }

    public record ListWatch(boolean watch) {
    }

    // S2C
    public record Member(long userId, String nickname) {
    }

    /** members[0]이 방장 */
    public record State(String roomId, String code, boolean friendsOnly, long hostUserId, List<Member> members, int maxMembers) {
    }

    public record Kicked() {
    }

    /** ROOM_LEAVE 응답. 방에 없었어도 보낸다 (클라이언트는 이걸 받기 전에 온 방 상태를 버린다) */
    public record Left() {
    }

    public record JoinFailed(String reason) {
    }

    public record Summary(String roomId, int members, int maxMembers) {
    }

    /** 만들어진 순서대로 (클라이언트가 #_001부터 번호를 붙인다) */
    public record RoomList(List<Summary> rooms) {
    }

    public static final class JoinFailReason {
        public static final String NOT_FOUND = "NOT_FOUND";     // 없는 방 코드
        public static final String CLOSED = "CLOSED";           // 목록에서 골랐는데 사라졌거나 친구 전용이 된 방
        public static final String FULL = "FULL";
        public static final String ALREADY_IN_ROOM = "ALREADY_IN_ROOM";

        private JoinFailReason() {
        }
    }

    private DevRoomPackets() {
    }
}
