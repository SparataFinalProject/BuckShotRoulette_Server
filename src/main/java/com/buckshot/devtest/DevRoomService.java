package com.buckshot.devtest;

import com.buckshot.devtest.DevRoomPackets.JoinFailReason;
import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.session.UserDisconnectedEvent;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 개발용 방: 멀티 로비의 방 만들기 / 방 목록 / 방 코드로 참가 / 추방 / 게임 시작.
 * 방은 최대 2명, 먼저 있던 사람이 방장(방장이 나가면 남은 사람이 방장). 친구 전용 방은 목록에 안 보이고 방 코드로만 들어온다.
 * 방 목록 화면을 연 사람(ROOM_LIST_WATCH)에게는 목록이 바뀔 때마다 ROOM_LIST를 다시 보낸다.
 * 게임을 시작하면 방은 없어지고 DevMatchService가 MATCH_FOUND부터 이어 간다. 정식 방 설계가 생기면 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevRoomService {

    private static final int MAX_MEMBERS = 2;
    private static final int CODE_DIGITS = 6;

    private final PacketSender packetSender;
    private final UserService userService;
    private final DevMatchService devMatchService;
    private final SecureRandom random = new SecureRandom();

    private static final class Room {
        final String id = UUID.randomUUID().toString();
        final String code;
        boolean friendsOnly;
        final List<Member> members = new ArrayList<>();   // [0]이 방장

        Room(String code) {
            this.code = code;
        }

        long hostId() {
            return members.get(0).userId();
        }
    }

    private final Map<String, Room> rooms = new LinkedHashMap<>();   // 만들어진 순서
    private final Map<Long, Room> roomByUser = new HashMap<>();
    private final Set<Long> watchers = new HashSet<>();

    public void create(long userId) {
        Member me = member(userId);
        synchronized (this) {
            Room room = roomByUser.get(userId);
            if (room == null) {
                room = new Room(newCode());
                room.members.add(me);
                rooms.put(room.id, room);
                roomByUser.put(userId, room);
                log.info("[dev] room created code={} host={}", room.code, userId);
            }
            sendState(room);
            broadcastList();
        }
    }

    public void join(long userId, String roomId, String code) {
        Member me = member(userId);
        boolean byCode = code != null && !code.isBlank();
        synchronized (this) {
            if (roomByUser.containsKey(userId)) {
                fail(userId, JoinFailReason.ALREADY_IN_ROOM);
                return;
            }
            Room room = byCode ? findByCode(code.trim()) : rooms.get(roomId);
            if (room == null || (!byCode && room.friendsOnly)) {
                fail(userId, byCode ? JoinFailReason.NOT_FOUND : JoinFailReason.CLOSED);
                if (!byCode) sendList(userId);   // 오래된 목록을 보고 눌렀으므로 바로 갱신
                return;
            }
            if (room.members.size() >= MAX_MEMBERS) {
                fail(userId, JoinFailReason.FULL);
                return;
            }
            room.members.add(me);
            roomByUser.put(userId, room);
            log.info("[dev] room joined code={} userId={}", room.code, userId);
            sendState(room);
            broadcastList();
        }
    }

    public synchronized void leave(long userId) {
        Room room = roomByUser.get(userId);
        if (room != null) {
            removeMember(room, userId);
            broadcastList();
        }
        packetSender.sendTo(userId, PacketType.ROOM_LEFT, new DevRoomPackets.Left());
    }

    public synchronized void setFriendsOnly(long userId, boolean friendsOnly) {
        Room room = roomByUser.get(userId);
        if (room == null || room.hostId() != userId || room.friendsOnly == friendsOnly) return;
        room.friendsOnly = friendsOnly;
        sendState(room);
        broadcastList();
    }

    public synchronized void kick(long userId, long targetId) {
        Room room = roomByUser.get(userId);
        if (room == null || room.hostId() != userId || targetId == userId || roomByUser.get(targetId) != room) return;
        removeMember(room, targetId);
        packetSender.sendTo(targetId, PacketType.ROOM_KICKED, new DevRoomPackets.Kicked());
        log.info("[dev] room kick code={} target={}", room.code, targetId);
        broadcastList();
    }

    public void start(long userId) {
        long host;
        long guest;
        synchronized (this) {
            Room room = roomByUser.get(userId);
            if (room == null || room.hostId() != userId || room.members.size() < MAX_MEMBERS) return;
            host = room.hostId();
            guest = room.members.get(1).userId();
            rooms.remove(room.id);
            roomByUser.remove(host);
            roomByUser.remove(guest);
            watchers.remove(host);
            watchers.remove(guest);
            log.info("[dev] room start code={} users={},{}", room.code, host, guest);
            broadcastList();
        }
        devMatchService.startPair(host, guest);
    }

    public synchronized void watch(long userId, boolean watch) {
        if (!watch) {
            watchers.remove(userId);
            return;
        }
        watchers.add(userId);
        sendList(userId);
    }

    @EventListener
    public synchronized void onDisconnected(UserDisconnectedEvent event) {
        long userId = event.userId();
        watchers.remove(userId);
        Room room = roomByUser.get(userId);
        if (room == null) return;
        removeMember(room, userId);
        broadcastList();
    }

    // 나간 사람을 빼고, 남은 사람이 있으면 그 사람이 방장이 된다. 아무도 없으면 방을 없앤다.
    private void removeMember(Room room, long userId) {
        room.members.removeIf(m -> m.userId() == userId);
        roomByUser.remove(userId);
        if (room.members.isEmpty()) {
            rooms.remove(room.id);
            log.info("[dev] room closed code={}", room.code);
            return;
        }
        sendState(room);
    }

    private void sendState(Room room) {
        var state = new DevRoomPackets.State(room.id, room.code, room.friendsOnly, room.hostId(), List.copyOf(room.members), MAX_MEMBERS);
        for (Member m : room.members) packetSender.sendTo(m.userId(), PacketType.ROOM_STATE, state);
    }

    private void fail(long userId, String reason) {
        packetSender.sendTo(userId, PacketType.ROOM_JOIN_FAILED, new DevRoomPackets.JoinFailed(reason));
    }

    private void broadcastList() {
        var list = list();
        for (long w : watchers) packetSender.sendTo(w, PacketType.ROOM_LIST, list);
    }

    private void sendList(long userId) {
        packetSender.sendTo(userId, PacketType.ROOM_LIST, list());
    }

    private DevRoomPackets.RoomList list() {
        var summaries = new ArrayList<DevRoomPackets.Summary>();
        for (Room r : rooms.values()) {
            if (!r.friendsOnly) summaries.add(new DevRoomPackets.Summary(r.id, r.members.size(), MAX_MEMBERS));
        }
        return new DevRoomPackets.RoomList(summaries);
    }

    private Room findByCode(String code) {
        for (Room r : rooms.values()) {
            if (r.code.equals(code)) return r;
        }
        return null;
    }

    private String newCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        String code;
        do {
            code = String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
        } while (findByCode(code) != null);
        return code;
    }

    private Member member(long userId) {
        return new Member(userId, userService.getProfile(userId).nickname());
    }
}
