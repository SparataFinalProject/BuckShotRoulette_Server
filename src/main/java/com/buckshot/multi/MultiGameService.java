package com.buckshot.multi;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.ChatMessage;
import com.buckshot.multi.MultiPackets.ItemGrabbed;
import com.buckshot.multi.MultiPackets.ItemPhaseBegin;
import com.buckshot.multi.MultiPackets.ItemPhaseDone;
import com.buckshot.multi.MultiPackets.ItemPlaced;
import com.buckshot.multi.MultiPackets.LoadShotgun;
import com.buckshot.multi.MultiPackets.LookAt;
import com.buckshot.multi.MultiPackets.RoundEnd;
import com.buckshot.multi.MultiPackets.RoundStart;
import com.buckshot.multi.MultiPackets.Shot;
import com.buckshot.multi.MultiPackets.ShotgunDropped;
import com.buckshot.multi.MultiPackets.ShotgunPicked;
import com.buckshot.multi.MultiPackets.Turn;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.session.UserDisconnectedEvent;
import java.util.ArrayDeque;
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
 * 3~4인 전용 게임 (원작 4인 모드 규칙을 따른 간단한 서버). 라운드 시작, 아이템 받기, 셸 장전, 차례 진행(총 집기, 대상 고르기, 사격, 체력), 라운드 종료가 있다.
 * 흐름: 방 시작(DevRoomService) -> 모두 MULTI_READY(씬 입장) -> MULTI_ROUND_START(라운드, 목숨, 차례 순서, 자리별 받을 개수)
 *      -> 모두 MULTI_INTRO_DONE(입장 연출 끝) -> MULTI_ITEM_PHASE_BEGIN -> 각자 MULTI_GRAB_ITEM / MULTI_PLACE_ITEM(칸)을 반복
 *      -> 자리마다 MULTI_ITEM_PHASE_DONE -> 모두 끝나고 2초 뒤 MULTI_LOAD_SHOTGUN(보이는 셸 순서). 서버가 아이템을 정하고 모든 자리에 같은 일을 알려 주므로 화면이 서로 같다.
 *      -> 차례마다 MULTI_PICKUP_SHOTGUN -> (MULTI_LOOK) -> MULTI_SHOOT -> MULTI_SHOT. 연출이 끝나는 고정 시간(원작 3.8초) 뒤 MULTI_TURN / 셸이 다 떨어졌으면 아이템 받기부터 다시 / 한 명만 남으면 MULTI_ROUND_END
 *      -> 다음 라운드(최대 3라운드). 첫 차례(장전 직후)는 MULTI_LOAD_SHOTGUN의 firstTurnSeat이고 따로 MULTI_TURN을 보내지 않는다.
 * 클라이언트의 연출 시간은 서버가 기다려 주지 않고 원작 고정 시간으로 맞춘다 (Timing).
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
    static final int LOAD_DELAY_SEC = 2;            // 모두 아이템 받기를 끝내고 장전이 시작하기까지 (원작 2초)
    static final int READY_TIMEOUT_SEC = 20;
    static final int INTRO_TIMEOUT_SEC = 40;
    static final int ROUNDS = 3;                    // 원작 라운드 수 (0~2)
    static final int CHAT_MAX_LENGTH = 100;         // 채팅 한 줄 최대 글자 수
    static final long CHAT_MIN_INTERVAL_MS = 500;   // 도배 방지: 한 사람이 이 시간 안에 또 보낸 채팅은 버린다

    /** 원작 MP_ItemResource: 아이템 번호, 한 사람 상한, 테이블 전체 상한 (배분하지 않는 유통기한 약(7)은 뺐다) */
    private record ItemRule(int id, int perSeat, int global) {
    }

    /** 원작 고정 시간 (초). 클라이언트 연출 길이와 맞춘 값이고, 테스트는 짧게 바꾼다 */
    record Timing(double loadToTurnSec, double turnStartSec, int turnTimeoutSec, int targetTimeoutSec, double shotSec, double dropSec,
            double winSec, double nextRoundItemSec) {
        /**
         * loadToTurn: 장전 패킷 뒤 셸 순서를 보여 주고(0.7+5+1.7) 첫 차례로 카메라가 가기까지(0.2+0.9). turnStart: 차례 패킷 뒤 카메라가 도착하기까지.
         * turnTimeout 20, targetTimeout 21: 원작 MAIN_timeout_duration_turn / _shotgun_target_selection. shot 3.8: 쏘는 연출(1인칭 3.6+0.2, 3인칭 0.25+1.9+1.65).
         * drop: 총을 내려놓는 연출. win 9.3: 원작 WinRoutine(0.5+2+6.4+0.4). nextRoundItem: 새 라운드 표시·체력·번개를 다시 켜고 아이템 받기까지(추정)
         */
        static final Timing ORIGINAL = new Timing(8.5, 1.1, 20, 21, 3.8, 1.0, 9.3, 6.0);
    }

    enum Phase {
        ITEMS, LOADING, TURN, TARGETING, DROPPING, SHOOTING, ROUND_END
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
    Timing timing = Timing.ORIGINAL;

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
        boolean left;                         // 연결이 끊겨 나갔다
        int health = STARTING_HEALTH;
        boolean holdingShotgun;               // 총을 집었다 (대상을 고르는 중)

        Seat(long userId) {
            this.userId = userId;
        }

        boolean alive() {
            return !left && health > 0;
        }
    }

    private static final class Game {
        final List<Seat> seats = new ArrayList<>();
        boolean started;       // MULTI_ROUND_START를 보냄
        boolean itemPhase;     // MULTI_ITEM_PHASE_BEGIN을 보냄
        boolean loadScheduled; // 모두 아이템 받기를 끝내 장전을 예약함
        boolean over;
        ShellDeck deck;                                    // 셸 묶음 뽑기 (게임 내내 이어진다)
        boolean[] shotgun = new boolean[0];                // 총 속의 실제 셸 순서 (true = 실탄). 서버만 안다
        int firstTurnSeat;                                 // 이번 라운드 선공 (원작 MAIN_active_first_turn_socket)
        int round;                                         // 0~2
        Phase phase = Phase.ITEMS;
        int turnSeat;
        final ArrayDeque<Boolean> chamber = new ArrayDeque<>();   // 총 속에 남은 셸 (앞이 다음에 나온다). 서버만 안다
        int token;                                         // 단계가 바뀔 때마다 올려서 지난 단계의 타이머를 무시한다
        boolean checkingFirstDeath = true;                 // 이번 라운드에 아직 아무도 안 죽었다 (원작 MAIN_active_checking_for_first_death)
        int firstDeadSeat = -1;                            // 지난 라운드에 처음 죽은 자리 (원작 MAIN_active_first_socket_to_die). 사격이 생기면 기록한다
    }

    private final Map<Long, Game> gameByUser = new HashMap<>();
    // 채팅은 아이템 받기가 끝나 게임이 정리된 뒤에도 이어져야 해서 따로 둔다 (연결이 끊기거나 새 게임이 만들어질 때까지)
    private final Map<Long, Game> chatGameByUser = new HashMap<>();

    public void create(List<Member> players) {
        Game game = new Game();
        for (Member m : players) game.seats.add(new Seat(m.userId()));
        game.deck = new ShellDeck(players.size(), random);
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

    /** 1대1 게임(DevMatchService)의 채팅만 연다. 자리 번호는 userId가 작은 쪽이 0이다 (클라이언트도 같은 규칙으로 찾는다). */
    public synchronized void openChat(long a, long b) {
        Game game = new Game();
        game.over = true;
        game.seats.add(new Seat(Math.min(a, b)));
        game.seats.add(new Seat(Math.max(a, b)));
        for (Seat s : game.seats) chatGameByUser.put(s.userId, game);
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
        seat.left = true;
        seat.ready = true;
        seat.introDone = true;
        seat.finished = true;
        if (game.seats.stream().noneMatch(s -> gameByUser.get(s.userId) == game)) {
            game.over = true;
            return;
        }
        seat.holdingShotgun = false;
        if (!game.started && game.seats.stream().allMatch(s -> s.ready)) startRound(game);
        else if (game.started && !game.itemPhase && game.phase == Phase.ITEMS && game.seats.stream().allMatch(s -> s.introDone)) beginItemPhase(game);
        else if (game.phase == Phase.ITEMS && game.itemPhase) checkItemPhaseOver(game);
        else if (game.phase == Phase.TURN || game.phase == Phase.TARGETING) {
            // 차례였던 사람이 나갔으면 넘기고, 한 명만 남았으면 라운드를 끝낸다
            int winner = winnerSeat(game);
            if (winner != -1) roundEnd(game, winner);
            else if (indexOf(game, seat) == game.turnSeat) beginTurn(game, nextTurn(game.turnSeat, alive(game)), true, "timeout");
        }
    }

    // ───── 단계 ─────

    private synchronized void startRound(Game game) {
        if (game.started || game.over) return;
        game.started = true;
        int n = game.seats.size();
        game.firstTurnSeat = pickFirstTurn(connected(game), game.firstDeadSeat, random);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        int[] counts = new int[n];
        int common = FIXED_ITEM_COUNT > 0 ? FIXED_ITEM_COUNT : randomItemCount(n);   // 원작처럼 라운드마다 한 번 정해 모든 자리가 같다
        for (int i = 0; i < n; i++) {
            counts[i] = common;
            game.seats.get(i).target = common;
        }
        log.info("[multi] round start counts={}", java.util.Arrays.toString(counts));
        broadcast(game, PacketType.MULTI_ROUND_START, new RoundStart(game.round, STARTING_HEALTH, order, counts));
        later(game, INTRO_TIMEOUT_SEC, () -> beginItemPhase(game));
    }

    /**
     * 원작 MainRoutine_StartRound (두 번째 라운드부터): 체력과 아이템 칸을 비우고 처음 죽은 사람이 선공이 된다. 입장 연출은 없어서 RoundStart 뒤
     * nextRoundItemSec 만에 바로 아이템 받기가 시작된다.
     */
    private void nextRound(Game game) {
        game.round++;
        game.token++;
        game.phase = Phase.ITEMS;
        game.itemPhase = false;
        game.loadScheduled = false;
        game.checkingFirstDeath = true;
        game.chamber.clear();
        int n = game.seats.size();
        game.firstTurnSeat = pickFirstTurn(connected(game), game.firstDeadSeat, random);
        int[] order = new int[n];
        int[] counts = new int[n];
        int common = FIXED_ITEM_COUNT > 0 ? FIXED_ITEM_COUNT : randomItemCount(n);
        for (int i = 0; i < n; i++) {
            Seat s = game.seats.get(i);
            order[i] = i;
            s.health = s.left ? 0 : STARTING_HEALTH;
            java.util.Arrays.fill(s.cells, 0);
            s.placed = 0;
            s.holding = 0;
            s.holdingShotgun = false;
            s.finished = s.left;
            s.target = s.left ? 0 : common;
            counts[i] = s.target;
        }
        log.info("[multi] round {} start counts={}", game.round, java.util.Arrays.toString(counts));
        broadcast(game, PacketType.MULTI_ROUND_START, new RoundStart(game.round, STARTING_HEALTH, order, counts));
        later(game, timing.nextRoundItemSec(), () -> beginItemPhase(game));
    }

    private synchronized void beginItemPhase(Game game) {
        if (game.itemPhase || game.over) return;
        game.itemPhase = true;
        game.phase = Phase.ITEMS;
        game.token++;
        int[] counts = new int[game.seats.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = game.seats.get(i).target;
        broadcast(game, PacketType.MULTI_ITEM_PHASE_BEGIN, new ItemPhaseBegin(ITEM_TIMEOUT_SEC, counts));
        later(game, ITEM_TIMEOUT_SEC, () -> endItemPhase(game));
        // 받을 것이 없는 자리(빈 칸이 없음)는 바로 끝난다
        for (Seat s : game.seats) {
            if (!s.finished && s.target == 0) finish(game, s, false);
        }
    }

    /** 원작: 셸이 다 떨어지면 빈 칸이 있는 살아 있는 자리가 아이템을 다시 받고 장전한다. 아이템은 테이블에 남아 있다 */
    private void regrabItems(Game game) {
        int common = FIXED_ITEM_COUNT > 0 ? FIXED_ITEM_COUNT : randomItemCount(game.seats.size());
        for (Seat s : game.seats) {
            s.holding = 0;
            s.placed = 0;
            if (!s.alive()) {
                s.target = 0;
                s.finished = true;
                continue;
            }
            int free = 0;
            for (int c : s.cells) if (c == 0) free++;
            s.target = Math.min(common, free);
            s.finished = false;
        }
        game.itemPhase = false;
        game.loadScheduled = false;
        beginItemPhase(game);
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
        if (game.loadScheduled || !game.seats.stream().allMatch(s -> s.finished)) return;
        log.info("[multi] item phase over");
        game.loadScheduled = true;
        later(game, LOAD_DELAY_SEC, () -> loadShotgun(game));
    }

    /** 원작 MainRoutine_LoadShotgun: 셸 순서와 선공을 모두에게 보내고, 클라이언트의 장전 연출이 끝나는 고정 시간 뒤 첫 차례가 시작된다 */
    private void loadShotgun(Game game) {
        if (game.over) return;
        game.token++;
        game.phase = Phase.LOADING;
        ShellDeck.Draw draw = game.deck.draw();
        game.shotgun = draw.inGun();
        game.chamber.clear();
        for (boolean live : draw.inGun()) game.chamber.add(live);
        // 원작 PassTurn: 선공이 그 사이 나갔거나 죽었으면 다음 자리로 넘긴다
        boolean[] alive = alive(game);
        int first = alive[game.firstTurnSeat] ? game.firstTurnSeat : nextTurn(game.firstTurnSeat, alive);
        game.firstTurnSeat = first;
        game.turnSeat = first;
        log.info("[multi] load shotgun visible={} firstTurn={}", java.util.Arrays.toString(draw.visible()), first);
        broadcast(game, PacketType.MULTI_LOAD_SHOTGUN, new LoadShotgun(draw.visible(), first));
        later(game, timing.loadToTurnSec(), () -> beginTurn(game, first, false, "start"));
    }

    // ───── 차례 진행 ─────

    /** 지난 단계의 타이머가 끼어들지 않게, 부른 시점의 단계(token)가 그대로일 때만 실행한다 */
    private void later(Game game, double seconds, Runnable action) {
        int token = game.token;
        TIMER.schedule(() -> {
            synchronized (this) {
                if (game.over || game.token != token) return;
                action.run();
            }
        }, Math.round(seconds * 1000), TimeUnit.MILLISECONDS);
    }

    /** announce가 false면(장전 직후 첫 차례) 클라이언트가 이미 스스로 차례를 넘겼으니 패킷을 보내지 않는다 */
    private void beginTurn(Game game, int seat, boolean announce, String reason) {
        int winner = winnerSeat(game);
        if (winner != -1) {
            roundEnd(game, winner);
            return;
        }
        game.token++;
        game.turnSeat = seat;
        game.phase = Phase.TURN;
        for (Seat s : game.seats) s.holdingShotgun = false;
        if (announce) broadcast(game, PacketType.MULTI_TURN, new Turn(seat, timing.turnTimeoutSec(), reason));
        later(game, timing.turnTimeoutSec() + (announce ? timing.turnStartSec() : 0), () -> onTurnTimeout(game, seat));
    }

    /** 원작 timeout exceeded(turn): 총을 집지 않고 제한 시간이 지나면 다음 사람에게 넘어간다 */
    private void onTurnTimeout(Game game, int seat) {
        if (game.phase != Phase.TURN || game.turnSeat != seat) return;
        log.info("[multi] turn timeout seat={}", seat);
        beginTurn(game, nextTurn(seat, alive(game)), true, "timeout");
    }

    /** 원작 pickup shotgun request(10) -> pickup shotgun(11) */
    public synchronized void pickupShotgun(long userId) {
        Game game = gameByUser.get(userId);
        if (game == null || game.over || game.phase != Phase.TURN) return;
        Seat seat = seat(game, userId);
        int idx = indexOf(game, seat);
        if (idx != game.turnSeat || !seat.alive()) return;
        game.token++;
        game.phase = Phase.TARGETING;
        seat.holdingShotgun = true;
        broadcast(game, PacketType.MULTI_SHOTGUN_PICKED, new ShotgunPicked(idx, timing.targetTimeoutSec()));
        later(game, timing.targetTimeoutSec(), () -> onTargetTimeout(game, idx));
    }

    /** 원작 look at user request(15) -> look at user(16). 보낸 사람은 이미 스스로 돌렸으니 나머지에게만 보낸다 */
    public synchronized void look(long userId, String direction) {
        Game game = gameByUser.get(userId);
        if (game == null || game.over || game.phase != Phase.TARGETING || direction == null) return;
        if (!direction.equals("left") && !direction.equals("forward") && !direction.equals("right")) return;
        Seat seat = seat(game, userId);
        int idx = indexOf(game, seat);
        if (idx != game.turnSeat || !seat.holdingShotgun) return;
        for (Seat s : game.seats) {
            if (s != seat && !s.left) packetSender.sendTo(s.userId, PacketType.MULTI_LOOK_AT, new LookAt(idx, direction));
        }
    }

    /** 원작 timeout exceeded(shotgun target selection): 대상을 고르지 못하면 총을 내려놓고 차례가 넘어간다 */
    private void onTargetTimeout(Game game, int seat) {
        if (game.phase != Phase.TARGETING || game.turnSeat != seat) return;
        log.info("[multi] target timeout seat={}", seat);
        game.token++;
        game.phase = Phase.DROPPING;
        game.seats.get(seat).holdingShotgun = false;
        broadcast(game, PacketType.MULTI_SHOTGUN_DROPPED, new ShotgunDropped(seat));
        later(game, timing.dropSec(), () -> beginTurn(game, nextTurn(seat, alive(game)), true, "timeout"));
    }

    /** 원작 shoot user request(12) -> shoot user(13). 실탄 여부는 이때 처음 공개된다 */
    public synchronized void shoot(long userId, int target) {
        Game game = gameByUser.get(userId);
        if (game == null || game.over || game.phase != Phase.TARGETING) return;
        Seat seat = seat(game, userId);
        int shooter = indexOf(game, seat);
        if (shooter != game.turnSeat || !seat.holdingShotgun) return;
        if (target < 0 || target >= game.seats.size() || !game.seats.get(target).alive()) return;
        game.token++;
        game.phase = Phase.SHOOTING;
        seat.holdingShotgun = false;
        boolean live = Boolean.TRUE.equals(game.chamber.pollFirst());
        Seat victim = game.seats.get(target);
        if (live) {
            victim.health = Math.max(0, victim.health - 1);
            if (victim.health == 0 && game.checkingFirstDeath) {
                game.firstDeadSeat = target;
                game.checkingFirstDeath = false;
            }
        }
        int shellsLeft = game.chamber.size();
        boolean keepTurn = target == shooter && !live;
        // 원작 CheckIfShooterEndingTurnAfterShot: 자기를 공포탄으로 쏜 경우만 차례가 그대로다 (셸이 비면 어쨌든 끝)
        boolean endsTurn = winnerSeat(game) != -1 || shellsLeft == 0 || !keepTurn;
        log.info("[multi] shot shooter={} target={} live={} left={} health={}", shooter, target, live, shellsLeft, java.util.Arrays.toString(healths(game)));
        broadcast(game, PacketType.MULTI_SHOT, new Shot(shooter, target, live, shellsLeft, healths(game), endsTurn));
        later(game, timing.shotSec(), () -> afterShot(game, shooter, keepTurn, shellsLeft));
    }

    /** 원작 CheckIfEndingTurn -> MainRoutine_UserEndTurn: 승자가 있으면 라운드 끝, 셸이 비면 아이템 받기, 아니면 다음 차례 */
    private void afterShot(Game game, int shooter, boolean keepTurn, int shellsLeft) {
        int winner = winnerSeat(game);
        if (winner != -1) {
            roundEnd(game, winner);
            return;
        }
        int next = keepTurn && game.seats.get(shooter).alive() ? shooter : nextTurn(shooter, alive(game));
        if (shellsLeft == 0) {
            game.firstTurnSeat = next;
            regrabItems(game);
            return;
        }
        beginTurn(game, next, true, next == shooter ? "keep" : "next");
    }

    private void roundEnd(Game game, int winner) {
        game.token++;
        game.phase = Phase.ROUND_END;
        boolean finalRound = game.round >= ROUNDS - 1;
        log.info("[multi] round {} end winner={} final={}", game.round, winner, finalRound);
        broadcast(game, PacketType.MULTI_ROUND_END, new RoundEnd(winner, game.round, finalRound));
        if (finalRound) later(game, timing.winSec(), () -> endGame(game));
        else later(game, timing.winSec(), () -> nextRound(game));
    }

    private void endGame(Game game) {
        game.over = true;
        for (Seat s : game.seats) gameByUser.remove(s.userId, game);
    }

    /** 테스트용: 총 속 셸을 정해 둔다 (true = 실탄) */
    synchronized void setChamberForTest(long userId, boolean... shells) {
        Game game = gameByUser.get(userId);
        game.chamber.clear();
        for (boolean live : shells) game.chamber.add(live);
    }

    /** 살아 있는 자리가 하나면 그 자리, 아니면 -1 */
    private static int winnerSeat(Game game) {
        int winner = -1;
        for (int i = 0; i < game.seats.size(); i++) {
            if (!game.seats.get(i).alive()) continue;
            if (winner != -1) return -1;
            winner = i;
        }
        return winner;
    }

    private static boolean[] alive(Game game) {
        boolean[] alive = new boolean[game.seats.size()];
        for (int i = 0; i < alive.length; i++) alive[i] = game.seats.get(i).alive();
        return alive;
    }

    private static int[] healths(Game game) {
        int[] health = new int[game.seats.size()];
        for (int i = 0; i < health.length; i++) health[i] = game.seats.get(i).health;
        return health;
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

    /** 원작 GetFirstTurn_Socket: 지난 라운드에 처음 죽은 사람이 아직 있으면 그 자리, 아니면 남아 있는 자리 중 무작위 */
    static int pickFirstTurn(boolean[] connected, int firstDeadSeat, Random random) {
        if (firstDeadSeat >= 0 && firstDeadSeat < connected.length && connected[firstDeadSeat]) return firstDeadSeat;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < connected.length; i++) if (connected[i]) candidates.add(i);
        if (candidates.isEmpty()) return 0;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 원작 GetNextTurn_Socket (CW): 자리 번호가 하나 큰 쪽으로, 끝이면 0부터. 나간 자리는 건너뛴다 */
    static int nextTurn(int from, boolean[] connected) {
        int n = connected.length;
        for (int step = 1; step <= n; step++) {
            int seat = (from + step) % n;
            if (connected[seat]) return seat;
        }
        return from;
    }

    private static boolean[] connected(Game game) {
        boolean[] connected = new boolean[game.seats.size()];
        for (int i = 0; i < connected.length; i++) connected[i] = !game.seats.get(i).left;
        return connected;
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
