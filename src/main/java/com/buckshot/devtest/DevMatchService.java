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
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 개발용 임시 매칭: 두 클라이언트를 인게임 씬까지 들여보내는 것만 확인한다.
 * MATCH_JOIN 두 명이 모이면 MATCH_FOUND, 둘 다 GAME_READY를 보내면 GAME_START까지만 보낸다.
 * 이후 진행은 DevGameRoom이 맡는다. 끊김: 대기 중이면 대기자에서 빼고, 준비 중이면 상대에게 MATCH_CANCELLED(OPPONENT_DISCONNECTED),
 * 게임 중이면 남은 쪽 승리(GAME_OVER DISCONNECT). MATCH_FOUND 뒤 8초 안에 둘 다 준비하지 않으면 둘 다에게 MATCH_CANCELLED(READY_TIMEOUT).
 * 대기열 타임아웃·취소는 없다. 정식 매칭(ServerDesign 6.3)과 GameRoom이 생기면 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevMatchService {

    private static final int HP = DevGameRoom.HP;
    private static final int MAX_ITEM_SLOTS = 8;
    private static final int QUEUE_TIMEOUT_SEC = 60;
    private static final int READY_TIMEOUT_SEC = 8;
    private static final int SKIN_COUNT = 3;   // 클라이언트 MultiGameManager.skins (Respirator, GasMask, WeldingMask)
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dev-ready-timer");
        t.setDaemon(true);
        return t;
    });

    private final PacketSender packetSender;
    private final UserService userService;
    private final SessionRegistry sessionRegistry;

    private Long waitingUserId;
    private int waitingSkin;   // 기다리는 사람 화면에 미리 보여 준 상대 마스크 = 나중에 들어오는 사람의 마스크
    private final Random random = new Random();
    private final Map<String, long[]> games = new HashMap<>();
    private final Map<String, Set<Long>> ready = new HashMap<>();
    // 게임 시작 전에 취소된 게임 -> 취소 사유. 취소를 페이드·씬 로딩 중에 받아 놓친 클라가 늦게 GAME_READY를 보내면 다시 알려 준다
    // (방에서 시작한 뒤 로비 페이드 중에는 MATCH_CANCELLED를 받는 곳이 없다)
    private final Map<String, String> cancelled = new HashMap<>();
    private final Map<Long, DevGameRoom> roomByUser = new HashMap<>();   // 새 게임이 시작되면 덮어쓴다

    // 먼저 기다리는 사람: 상대 마스크를 3개 중 무작위로 정해 MATCH_QUEUED로 알려 준다 (기다리는 동안 그 마스크를 보여 줌).
    // 나중에 들어온 사람: 그 마스크가 자기 마스크가 되고, 기다리던 사람의 마스크는 나머지 두 개 중에서 무작위로 정한다
    public void join(long userId) {
        long waiter;
        int joinerSkin;
        int waiterSkin;
        synchronized (this) {
            // 대기 중에 연결이 끊긴 유저(Play를 끈 클라 등)는 버린다 (끊김 처리가 없어서 여기서 확인)
            if (waitingUserId != null && sessionRegistry.find(waitingUserId) == null) {
                log.info("[dev] drop disconnected waiting userId={}", waitingUserId);
                waitingUserId = null;
            }
            if (waitingUserId == null || waitingUserId == userId) {
                if (waitingUserId == null) waitingSkin = random.nextInt(SKIN_COUNT);
                waitingUserId = userId;
                packetSender.sendTo(userId, PacketType.MATCH_QUEUED,
                        new MatchQueuedPacket(System.currentTimeMillis(), QUEUE_TIMEOUT_SEC, waitingSkin));
                log.info("[dev] match queued userId={} opponentSkin={}", userId, waitingSkin);
                return;
            }
            waiter = waitingUserId;
            joinerSkin = waitingSkin;
            waiterSkin = otherSkin(joinerSkin);
            waitingUserId = null;
            packetSender.sendTo(userId, PacketType.MATCH_QUEUED,
                    new MatchQueuedPacket(System.currentTimeMillis(), QUEUE_TIMEOUT_SEC, waiterSkin));
        }
        startPair(waiter, userId, waiterSkin, joinerSkin);
    }

    /** 두 사람을 바로 짝지어 MATCH_FOUND를 보낸다 (방에서 게임 시작). 마스크는 서로 다른 두 개를 무작위로 */
    public void startPair(long a, long b) {
        int skinB = random.nextInt(SKIN_COUNT);
        startPair(a, b, otherSkin(skinB), skinB);
    }

    // skinA, skinB: 각자의 마스크 (상대 화면에 보이는 것)
    private void startPair(long a, long b, int skinA, int skinB) {
        long[] pair = new long[] { a, b };
        String gameId = UUID.randomUUID().toString();
        synchronized (this) {
            if (waitingUserId != null && (waitingUserId == a || waitingUserId == b)) waitingUserId = null;
            games.put(gameId, pair);
            ready.put(gameId, new HashSet<>());
        }
        log.info("[dev] match found gameId={} users={},{}", gameId, pair[0], pair[1]);
        packetSender.sendTo(pair[0], PacketType.MATCH_FOUND, new MatchFoundPacket(gameId, profile(pair[1]), READY_TIMEOUT_SEC, skinB));
        packetSender.sendTo(pair[1], PacketType.MATCH_FOUND, new MatchFoundPacket(gameId, profile(pair[0]), READY_TIMEOUT_SEC, skinA));
        TIMER.schedule(() -> readyTimeout(gameId), READY_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    // MATCH_FOUND 뒤 READY_TIMEOUT_SEC 안에 둘 다 GAME_READY를 보내지 않으면 둘 다에게 매칭 취소.
    // 이미 시작했거나 끊김으로 취소된 게임이면 아무것도 하지 않는다
    private void readyTimeout(String gameId) {
        long[] pair;
        synchronized (this) {
            if (!ready.containsKey(gameId)) return;
            ready.remove(gameId);
            pair = games.remove(gameId);
            cancelled.put(gameId, MatchCancelReason.READY_TIMEOUT);
        }
        log.info("[dev] ready timeout gameId={} users={},{}", gameId, pair[0], pair[1]);
        var cancelled = new MatchCancelledPacket(MatchCancelReason.READY_TIMEOUT);
        packetSender.sendTo(pair[0], PacketType.MATCH_CANCELLED, cancelled);
        packetSender.sendTo(pair[1], PacketType.MATCH_CANCELLED, cancelled);
    }

    // 주어진 마스크를 뺀 나머지 중 하나
    private int otherSkin(int skin) {
        return (skin + 1 + random.nextInt(SKIN_COUNT - 1)) % SKIN_COUNT;
    }

    public void ready(long userId, String gameId) {
        long[] pair;
        synchronized (this) {
            pair = games.get(gameId);
            Set<Long> readyUsers = ready.get(gameId);
            String cancelReason = pair == null ? cancelled.remove(gameId) : null;
            if (cancelReason != null) {
                log.info("[dev] late ready after cancel userId={} gameId={} reason={}", userId, gameId, cancelReason);
                packetSender.sendTo(userId, PacketType.MATCH_CANCELLED, new MatchCancelledPacket(cancelReason));
                return;
            }
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
                    cancelled.put(game.getKey(), MatchCancelReason.OPPONENT_DISCONNECTED);
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
