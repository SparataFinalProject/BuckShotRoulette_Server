package com.buckshot.devtest;

import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.MatchCancelReason;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.dto.ItemSlot;
import com.buckshot.ws.packet.dto.PlayerProfile;
import com.buckshot.ws.packet.dto.PlayerState;
import com.buckshot.ws.packet.s2c.GameStartPacket;
import com.buckshot.ws.packet.s2c.MatchCancelledPacket;
import com.buckshot.ws.packet.s2c.MatchFoundPacket;
import com.buckshot.ws.packet.s2c.MatchQueuedPacket;
import com.buckshot.ws.session.SessionRegistry;
import com.buckshot.ws.session.UserDisconnectedEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 개발용 임시 매칭: 두 클라이언트를 인게임 씬까지 들여보내는 것만 확인한다.
 * MATCH_JOIN 두 명이 모이면 MATCH_FOUND, 둘 다 GAME_READY를 보내면 GAME_START까지만 보낸다.
 * 이후 진행은 DevGameRoom이 맡는다. 끊김: 대기 중이면 대기자에서 빼고, 준비 중이면 상대에게 MATCH_CANCELLED(OPPONENT_DISCONNECTED),
 * 게임 중이면 남은 쪽 승리(GAME_OVER DISCONNECT). 대기열 타임아웃·취소는 없다. 정식 매칭(ServerDesign 6.3)과 GameRoom이 생기면 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevMatchService {

    private static final int HP = DevGameRoom.HP;
    private static final int MAX_ITEM_SLOTS = 8;
    private static final int QUEUE_TIMEOUT_SEC = 60;
    private static final int READY_TIMEOUT_SEC = 15;

    private final PacketSender packetSender;
    private final UserService userService;
    private final SessionRegistry sessionRegistry;

    private Long waitingUserId;
    private final Map<String, long[]> games = new HashMap<>();
    private final Map<String, Set<Long>> ready = new HashMap<>();
    private final Map<Long, DevGameRoom> roomByUser = new HashMap<>();   // 새 게임이 시작되면 덮어쓴다

    public void join(long userId) {
        long[] pair;
        synchronized (this) {
            packetSender.sendTo(userId, PacketType.MATCH_QUEUED, new MatchQueuedPacket(System.currentTimeMillis(), QUEUE_TIMEOUT_SEC));
            // 대기 중에 연결이 끊긴 유저(Play를 끈 클라 등)는 버린다 (끊김 처리가 없어서 여기서 확인)
            if (waitingUserId != null && sessionRegistry.find(waitingUserId) == null) {
                log.info("[dev] drop disconnected waiting userId={}", waitingUserId);
                waitingUserId = null;
            }
            if (waitingUserId == null || waitingUserId == userId) {
                waitingUserId = userId;
                log.info("[dev] match queued userId={}", userId);
                return;
            }
            pair = new long[] { waitingUserId, userId };
            waitingUserId = null;
        }
        startPair(pair[0], pair[1]);
    }

    /** 두 사람을 바로 짝지어 MATCH_FOUND를 보낸다 (자동 매칭, 방에서 게임 시작). */
    public void startPair(long a, long b) {
        long[] pair = new long[] { a, b };
        String gameId = UUID.randomUUID().toString();
        synchronized (this) {
            if (waitingUserId != null && (waitingUserId == a || waitingUserId == b)) waitingUserId = null;
            games.put(gameId, pair);
            ready.put(gameId, new HashSet<>());
        }
        log.info("[dev] match found gameId={} users={},{}", gameId, pair[0], pair[1]);
        packetSender.sendTo(pair[0], PacketType.MATCH_FOUND, new MatchFoundPacket(gameId, profile(pair[1]), READY_TIMEOUT_SEC));
        packetSender.sendTo(pair[1], PacketType.MATCH_FOUND, new MatchFoundPacket(gameId, profile(pair[0]), READY_TIMEOUT_SEC));
    }

    public void ready(long userId, String gameId) {
        long[] pair;
        synchronized (this) {
            pair = games.get(gameId);
            Set<Long> readyUsers = ready.get(gameId);
            if (pair == null || readyUsers == null || (pair[0] != userId && pair[1] != userId)) {
                log.info("[dev] ready ignored userId={} gameId={}", userId, gameId);
                return;
            }
            readyUsers.add(userId);
            log.info("[dev] ready userId={} gameId={} ({}/2)", userId, gameId, readyUsers.size());
            if (readyUsers.size() < 2) return;
            ready.remove(gameId);
        }

        var start = new GameStartPacket(gameId, MAX_ITEM_SLOTS, new PlayerState[] { state(pair[0]), state(pair[1]) });
        packetSender.sendTo(pair[0], PacketType.GAME_START, start);
        packetSender.sendTo(pair[1], PacketType.GAME_START, start);
        log.info("[dev] game start gameId={}", gameId);

        var room = new DevGameRoom(packetSender, pair[0], pair[1]);
        synchronized (this) {
            roomByUser.put(pair[0], room);
            roomByUser.put(pair[1], room);
        }
        room.begin();
    }

    @EventListener
    public void onDisconnected(UserDisconnectedEvent event) {
        long userId = event.userId();
        DevGameRoom room;
        long cancelTo = 0;
        synchronized (this) {
            if (waitingUserId != null && waitingUserId == userId) waitingUserId = null;

            // 준비 중(아직 GAME_START 전)에 나가면 상대는 매칭 취소
            for (var it = games.entrySet().iterator(); it.hasNext(); ) {
                var game = it.next();
                long[] pair = game.getValue();
                boolean pending = ready.containsKey(game.getKey());
                if (pending && (pair[0] == userId || pair[1] == userId)) {
                    cancelTo = pair[0] == userId ? pair[1] : pair[0];
                    ready.remove(game.getKey());
                    it.remove();
                }
            }

            room = roomByUser.remove(userId);
        }

        if (cancelTo != 0) {
            log.info("[dev] ready cancelled, userId={} left, notify {}", userId, cancelTo);
            packetSender.sendTo(cancelTo, PacketType.MATCH_CANCELLED, new MatchCancelledPacket(MatchCancelReason.OPPONENT_DISCONNECTED));
        }
        if (room != null) room.disconnect(userId);   // 게임 중이면 남은 쪽 승리, 이미 끝난 방이면 무시된다
    }

    public void fire(long userId, String target) {
        DevGameRoom room = roomOf(userId);
        if (room != null) room.fire(userId, target);
    }

    public void gunPickup(long userId) {
        DevGameRoom room = roomOf(userId);
        if (room != null) room.gunPickup(userId);
    }

    public void aim(long userId, String target) {
        DevGameRoom room = roomOf(userId);
        if (room != null) room.aim(userId, target);
    }

    public void useItem(long userId, int slot) {
        DevGameRoom room = roomOf(userId);
        if (room != null) room.useItem(userId, slot);
    }

    public void placeItem(long userId, int slot) {
        DevGameRoom room = roomOf(userId);
        if (room != null) room.placeItem(userId, slot);
    }

    private synchronized DevGameRoom roomOf(long userId) {
        DevGameRoom room = roomByUser.get(userId);
        if (room == null) log.info("[dev] request outside game userId={}", userId);
        return room;
    }

    private PlayerProfile profile(long userId) {
        PlayerProfileResponse p = userService.getProfile(userId);
        return new PlayerProfile(p.userId(), p.nickname(), p.wins(), p.losses(), p.rating(), p.tier());
    }

    private PlayerState state(long userId) {
        return new PlayerState(userId, userService.getProfile(userId).nickname(), HP, HP, new ItemSlot[0], 0);
    }
}
