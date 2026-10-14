package com.buckshot.multi;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.ChatMessage;
import com.buckshot.multi.MultiPackets.ItemGrabbed;
import com.buckshot.multi.MultiPackets.ItemPhaseBegin;
import com.buckshot.multi.MultiPackets.ItemPhaseDone;
import com.buckshot.multi.MultiPackets.ItemPlaced;
import com.buckshot.multi.MultiPackets.RoundStart;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.session.UserDisconnectedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 3~4인 전용 게임 (원작 4인 모드 규칙을 따른 간단한 서버). 지금은 라운드 시작과 아이템 받기까지만 있다.
 * 흐름: 방 시작(DevRoomService) -> 모두 MULTI_READY(씬 입장) -> MULTI_ROUND_START(라운드, 목숨, 차례 순서, 자리별 받을 개수)
 *      -> 모두 MULTI_INTRO_DONE(입장 연출 끝) -> MULTI_ITEM_PHASE_BEGIN -> 각자 MULTI_GRAB_ITEM / MULTI_PLACE_ITEM(칸)을 반복
 *      -> 자리마다 MULTI_ITEM_PHASE_DONE. 서버가 아이템을 정하고 모든 자리에 같은 일을 알려 주므로 화면이 서로 같다.
 * 사람이 안 오거나 느려도 멈추지 않게 각 단계에 제한 시간이 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiGameService {

    static final int SLOTS = 8;
    static final int STARTING_HEALTH = 4;
    static final int ITEM_TIMEOUT_SEC = 12;         // 원작 MAIN_timeout_duration_item_distribution은 30초지만 테스트하기 쉽게 줄였다
    static final int FIXED_ITEM_COUNT = 4;          // 이번 라운드 모두가 받을 개수 (0이면 원작 규칙의 무작위: 3인 3~5, 4인 3~4). 무작위여도 한 번만 정해 모두 같다
    static final int READY_TIMEOUT_SEC = 20;
    static final int INTRO_TIMEOUT_SEC = 40;
    static final int CHAT_MAX_LENGTH = 100;         // 채팅 한 줄 최대 글자 수
    static final long CHAT_MIN_INTERVAL_MS = 500;   // 도배 방지: 한 사람이 이 시간 안에 또 보낸 채팅은 버린다

    /** 원작 MP_ItemResource: 아이템 번호, 한 사람 상한, 테이블 전체 상한 (배분하지 않는 유통기한 약(7)은 뺐다) */
    private record ItemRule(int id, int perSeat, int global) {
    }

    private static final List<ItemRule> RULES = List.of(
            new ItemRule(1, 2, 64), new ItemRule(2, 2, 64), new ItemRule(3, 1, 1), new ItemRule(4, 1, 64), new ItemRule(5, 8, 64),
            new ItemRule(6, 8, 64), new ItemRule(8, 4, 64), new ItemRule(9, 4, 64), new ItemRule(10, 1, 2));

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "multi-game-timer");
        t.setDaemon(true);
        return t;
    });

    private final PacketSender packetSender;
    private final Random random = new Random();

    private static final class Seat {
        final long userId;
        final int[] cells = new int[SLOTS];   // 0 = 빈 칸, 아니면 아이템 번호
        int target;                           // 받을 개수
        int placed;
        int holding;                          // 꺼내서 들고 있는 아이템 (0 = 없음)
        boolean ready;
        boolean introDone;
        boolean finished;                     // 아이템 받기 끝
        long lastChatAt;                      // 마지막으로 채팅을 방송한 시각 (ms)

        Seat(long userId) {
            this.userId = userId;
        }
    }

    private static final class Game {
        final List<Seat> seats = new ArrayList<>();
        boolean started;       // MULTI_ROUND_START를 보냄
        boolean itemPhase;     // MULTI_ITEM_PHASE_BEGIN을 보냄
        boolean over;
    }

    private final Map<Long, Game> gameByUser = new HashMap<>();
    // 채팅은 아이템 받기가 끝나 게임이 정리된 뒤에도 이어져야 해서 따로 둔다 (연결이 끊기거나 새 게임이 만들어질 때까지)
    private final Map<Long, Game> chatGameByUser = new HashMap<>();

    public void create(List<Member> players) {
        Game game = new Game();
        for (Member m : players) game.seats.add(new Seat(m.userId()));
        synchronized (this) {
            for (Seat s : game.seats) {
                gameByUser.put(s.userId, game);
                chatGameByUser.put(s.userId, game);
            }
        }
        log.info("[multi] game created users={}", players.stream().map(Member::userId).toList());
        TIMER.schedule(() -> startRound(game), READY_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    public synchronized void ready(long userId) {
        Game game = gameByUser.get(userId);
        if (game == null || game.started) return;
        seat(game, userId).ready = true;
        if (game.seats.stream().allMatch(s -> s.ready)) startRound(game);
    }

    public synchronized void introDone(long userId) {
        Game game = gameByUser.get(userId);
        if (game == null || !game.started || game.itemPhase) return;
        seat(game, userId).introDone = true;
        if (game.seats.stream().allMatch(s -> s.introDone)) beginItemPhase(game);
    }

    /** 원작 GrabItemRequest -> GetItemToGrab. 꺼낼 수 있는 아이템이 없으면 그 자리는 끝난다 */
    public synchronized void grabItem(long userId) {
        Game game = gameByUser.get(userId);
        if (game == null || !game.itemPhase) return;
        Seat seat = seat(game, userId);
        if (seat.finished || seat.holding != 0 || seat.placed >= seat.target) return;
        int itemId = pickItem(game, seat);
        if (itemId == 0) {
            finish(game, seat, false);
            return;
        }
        seat.holding = itemId;
        broadcast(game, PacketType.MULTI_ITEM_GRABBED, new ItemGrabbed(indexOf(game, seat), itemId));
    }

    public synchronized void placeItem(long userId, int cell) {
        Game game = gameByUser.get(userId);
        if (game == null || !game.itemPhase) return;
        Seat seat = seat(game, userId);
        if (seat.finished || seat.holding == 0 || cell < 0 || cell >= SLOTS || seat.cells[cell] != 0) return;
        int itemId = seat.holding;
        seat.cells[cell] = itemId;
        seat.holding = 0;
        seat.placed++;
        boolean last = seat.placed >= seat.target || seat.placed >= SLOTS;
        broadcast(game, PacketType.MULTI_ITEM_PLACED, new ItemPlaced(indexOf(game, seat), cell, itemId, last));
        if (last) finish(game, seat, false);
    }

    /** 같은 게임 참가자 모두에게 채팅을 보낸다. 닉네임은 클라이언트가 방 정보에서 자리 번호로 찾는다. */
    public synchronized void chat(long userId, String text) {
        Game game = chatGameByUser.get(userId);
        if (game == null || text == null) return;
        String clean = sanitizeChat(text);
        if (clean.isEmpty()) return;
        Seat seat = seat(game, userId);
        long now = System.currentTimeMillis();
        if (now - seat.lastChatAt < CHAT_MIN_INTERVAL_MS) return;
        seat.lastChatAt = now;
        for (Seat s : game.seats) {
            if (chatGameByUser.get(s.userId) == game) packetSender.sendTo(s.userId, PacketType.MULTI_CHAT_MSG, new ChatMessage(indexOf(game, seat), clean));
        }
    }

    /** 제어 문자를 빼고 앞뒤 공백을 자르고 최대 글자 수로 줄인다 */
    static String sanitizeChat(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (clean.codePointCount(0, clean.length()) > CHAT_MAX_LENGTH) clean = clean.substring(0, clean.offsetByCodePoints(0, CHAT_MAX_LENGTH));
        return clean;
    }

    @EventListener
    public synchronized void onDisconnected(UserDisconnectedEvent event) {
        chatGameByUser.remove(event.userId());
        Game game = gameByUser.remove(event.userId());
        if (game == null) return;
        Seat seat = seat(game, event.userId());
        log.info("[multi] user left userId={}", event.userId());
        // 나간 사람을 기다리지 않는다
        seat.ready = true;
        seat.introDone = true;
        seat.finished = true;
        if (game.seats.stream().noneMatch(s -> gameByUser.get(s.userId) == game)) {
            game.over = true;
            return;
        }
        if (!game.started && game.seats.stream().allMatch(s -> s.ready)) startRound(game);
        else if (game.started && !game.itemPhase && game.seats.stream().allMatch(s -> s.introDone)) beginItemPhase(game);
        else if (game.itemPhase) checkItemPhaseOver(game);
    }

    // ───── 단계 ─────

    private synchronized void startRound(Game game) {
        if (game.started || game.over) return;
        game.started = true;
        int n = game.seats.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        int[] counts = new int[n];
        int common = FIXED_ITEM_COUNT > 0 ? FIXED_ITEM_COUNT : randomItemCount(n);   // 원작처럼 라운드마다 한 번 정해 모든 자리가 같다
        for (int i = 0; i < n; i++) {
            counts[i] = common;
            game.seats.get(i).target = common;
        }
        log.info("[multi] round start counts={}", java.util.Arrays.toString(counts));
        broadcast(game, PacketType.MULTI_ROUND_START, new RoundStart(0, STARTING_HEALTH, order, counts));
        TIMER.schedule(() -> beginItemPhase(game), INTRO_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private synchronized void beginItemPhase(Game game) {
        if (game.itemPhase || game.over) return;
        game.itemPhase = true;
        broadcast(game, PacketType.MULTI_ITEM_PHASE_BEGIN, new ItemPhaseBegin(ITEM_TIMEOUT_SEC));
        TIMER.schedule(() -> endItemPhase(game), ITEM_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private synchronized void endItemPhase(Game game) {
        for (Seat s : game.seats) {
            if (!s.finished) finish(game, s, true);
        }
    }

    private void finish(Game game, Seat seat, boolean timedOut) {
        if (seat.finished) return;
        seat.finished = true;
        seat.holding = 0;
        broadcast(game, PacketType.MULTI_ITEM_PHASE_DONE, new ItemPhaseDone(indexOf(game, seat), timedOut));
        checkItemPhaseOver(game);
    }

    private void checkItemPhaseOver(Game game) {
        if (game.seats.stream().allMatch(s -> s.finished)) {
            log.info("[multi] item phase over");
            // 이 다음 단계(셸 장전, 차례)는 아직 없다
            game.over = true;
            for (Seat s : game.seats) gameByUser.remove(s.userId, game);
        }
    }

    /** 원작 GetItemToGrab: 배분하는 아이템 중 그 사람의 개수와 테이블 전체 개수가 상한보다 적은 것에서 무작위 */
    private int pickItem(Game game, Seat seat) {
        List<Integer> pool = new ArrayList<>();
        for (ItemRule rule : RULES) {
            int mine = count(seat, rule.id());
            int all = 0;
            for (Seat s : game.seats) all += count(s, rule.id());
            if (mine < rule.perSeat() && all < rule.global()) pool.add(rule.id());
        }
        return pool.isEmpty() ? 0 : pool.get(random.nextInt(pool.size()));
    }

    private static int count(Seat seat, int id) {
        int n = 0;
        for (int c : seat.cells) if (c == id) n++;
        return n;
    }

    /** 원작 GetRandomItemGrabAmount (양 끝 포함): 3인 3~5, 4인 3~4 */
    private int randomItemCount(int players) {
        return switch (players) {
            case 3 -> 3 + random.nextInt(3);
            case 4 -> 3 + random.nextInt(2);
            default -> 2 + random.nextInt(3);
        };
    }

    private void broadcast(Game game, String type, Object data) {
        for (Seat s : game.seats) packetSender.sendTo(s.userId, type, data);
    }

    private static Seat seat(Game game, long userId) {
        for (Seat s : game.seats) if (s.userId == userId) return s;
        throw new IllegalStateException("not in game: " + userId);
    }

    private static int indexOf(Game game, Seat seat) {
        return game.seats.indexOf(seat);
    }
}
