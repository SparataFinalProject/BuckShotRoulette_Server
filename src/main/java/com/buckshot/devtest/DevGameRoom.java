package com.buckshot.devtest;

import com.buckshot.game.GameRules;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.ShellType;
import com.buckshot.ws.packet.Target;
import com.buckshot.ws.packet.dto.ItemSlot;
import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.s2c.ErrorPacket;
import com.buckshot.ws.packet.s2c.FireResultPacket;
import com.buckshot.ws.packet.s2c.GameOverPacket;
import com.buckshot.ws.packet.s2c.ItemPlacedPacket;
import com.buckshot.ws.packet.s2c.ItemsGrantedPacket;
import com.buckshot.ws.packet.s2c.OpponentAimPacket;
import com.buckshot.ws.packet.s2c.OpponentGunPickupPacket;
import com.buckshot.ws.packet.s2c.RoundStartPacket;
import com.buckshot.ws.packet.s2c.ShellRevealedPacket;
import com.buckshot.ws.packet.s2c.TurnStartPacket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 임시 게임 방: 두 클라이언트가 한 판을 끝까지 진행해 보는 용도.
 * 규칙은 클라이언트의 MultiMockInGameClient와 같다 (원작 규칙: 체력 6, 수갑은 한 번 건너뛰고 다음 차례에 부숨, 이미 찬 상대에겐 못 씀(중첩 불가),
 * 쇠톱은 다음 한 발 피해 2, 첫 라운드는 아이템 없음, 다시 장전하면 수갑이 풀림). 게임 중 한쪽이 나가면 남은 쪽 승리(DISCONNECT).
 * 패킷 순서: ITEMS_GRANTED ×2 (종류만, slot -1) -> 두 플레이어가 PLACE_ITEM으로 칸을 하나씩 고름 (상대에게 ITEM_PLACED)
 * -> 둘 다 다 놓거나 제한 시간이 지나면(남은 것은 서버가 앞쪽 빈 칸에 놓고 ITEM_PLACED auto) ROUND_START -> TURN_START.
 * 맥주로 뺀 탄은 ITEM_USED.shell로 둘 다에게 공개한다 (DevItemUsedPacket).
 * 정식 GameRoom(ServerDesign 6.4)과 팀원의 game 규칙 코어가 연결되면 지운다. 방 단위 synchronized로 두 플레이어 요청을 직렬화한다.
 * 연승 모드: 한 자리를 딜러(DevStreakGame.DEALER_ID)로 두고 {@link Listener}로 딜러 행동과 기록 저장을 붙인다 (방 규칙은 그대로).
 */
@Slf4j
public class DevGameRoom {

    static final int HP = GameRules.MAX_HP;
    private static final int SLOT_COUNT = GameRules.MAX_ITEM_SLOTS;
    private static final String[] ITEM_POOL = {
            ItemType.MAGNIFIER, ItemType.BEER, ItemType.CIGARETTE, ItemType.SAW, ItemType.HANDCUFFS };
    // 아이템을 다 놓을 때까지 기다리는 시간 (ITEMS_GRANTED를 보낸 때부터. 클라는 받기 전에 탄피 치우기·수갑 풀기·카메라 이동을 먼저 한다)
    private static final int PLACE_TIME_LIMIT_SEC = 25;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dev-place-timer");
        t.setDaemon(true);
        return t;
    });

    private final PacketSender sender;
    private final Listener listener;
    private final long[] users;
    private final Random random = new Random();

    private final Deque<String> shells = new ArrayDeque<>();   // 앞이 약실
    private final Map<Long, Integer> hp = new HashMap<>();
    private final Map<Long, String[]> items = new HashMap<>();
    private final Map<Long, Boolean> cuffed = new HashMap<>();
    private final Map<Long, Boolean> cuffSkipped = new HashMap<>();
    private boolean sawed;

    // 아이템 배치 중: 지급받았지만 아직 칸을 안 고른 아이템 (앞부터 차례로 놓는다)
    private final Map<Long, Deque<String>> toPlace = new HashMap<>();
    private final Map<Long, Integer> placedCount = new HashMap<>();
    private boolean placing;
    private int placingId;          // 지난 배치의 타이머가 늦게 실행돼도 무시하려고
    private long nextFirstTurn;     // 배치가 끝나면 시작할 턴
    private long turnUserId;
    private int round;
    private int turnNumber;
    private int grantCount;
    private boolean over;

    public DevGameRoom(PacketSender sender, long userA, long userB) {
        this(sender, userA, userB, Listener.NONE);
    }

    public DevGameRoom(PacketSender sender, long userA, long userB, Listener listener) {
        this.sender = sender;
        this.listener = listener;
        this.users = new long[] { userA, userB };
        for (long id : users) {
            hp.put(id, HP);
            items.put(id, new String[SLOT_COUNT]);
            cuffed.put(id, false);
            cuffSkipped.put(id, false);
        }
    }

    /** GAME_START를 보낸 직후 호출: 첫 라운드를 시작한다. */
    public synchronized void begin() {
        startRound(users[random.nextInt(2)]);
    }

    /** 게임 중 한쪽이 나가면(연결 끊김) 남은 쪽이 바로 승리한다. 이미 끝난 게임이면 아무것도 하지 않는다. */
    public synchronized void disconnect(long leftUserId) {
        if (over) return;
        over = true;
        long winner = other(leftUserId);
        log.info("[dev] disconnect room={}v{} left={} winner={}", users[0], users[1], leftUserId, winner);
        sender.sendTo(winner, PacketType.GAME_OVER, listener.gameOver(winner, GameOverReason.DISCONNECT));
    }

    /** 총을 집었다: 판정과 상관없는 연출용 알림이라 내 턴일 때만 상대에게 그대로 전달한다 (틀린 요청은 조용히 무시). */
    public synchronized void gunPickup(long userId) {
        if (over || userId != turnUserId) return;
        sender.sendTo(other(userId), PacketType.OPPONENT_GUN_PICKUP, new OpponentGunPickupPacket(userId));
    }

    /** 대상을 골라 겨누기 시작했다: 위와 같이 상대에게 전달한다. */
    public synchronized void aim(long userId, String target) {
        if (over || userId != turnUserId) return;
        if (!Target.SELF.equals(target) && !Target.OPPONENT.equals(target)) return;
        sender.sendTo(other(userId), PacketType.OPPONENT_AIM, new OpponentAimPacket(userId, target));
    }

    /** @return 발사했으면 true (차례가 아니거나 대상이 틀리면 false, 에러를 보냄) */
    public synchronized boolean fire(long userId, String target) {
        if (!checkTurn(userId, PacketType.FIRE)) return false;
        if (!Target.SELF.equals(target) && !Target.OPPONENT.equals(target)) {
            error(userId, "INVALID_MESSAGE", PacketType.FIRE);
            return false;
        }

        long other = other(userId);
        long victim = Target.SELF.equals(target) ? userId : other;

        String shell = shells.pollFirst();
        int damage = ShellType.LIVE.equals(shell) ? (sawed ? 2 : 1) : 0;
        sawed = false;
        int victimHp = Math.max(0, hp.get(victim) - damage);
        hp.put(victim, victimHp);
        boolean extraTurn = victim == userId && ShellType.BLANK.equals(shell);

        broadcast(PacketType.FIRE_RESULT,
                new FireResultPacket(userId, victim, shell, damage, victimHp, shells.size(), extraTurn));
        log.info("[dev] fire room={} shooter={} victim={} shell={} hp={}", users[0] + "v" + users[1], userId, victim, shell, victimHp);
        listener.fired(userId, victim, shell, damage);

        if (victimHp <= 0) {
            over = true;
            broadcast(PacketType.GAME_OVER, listener.gameOver(other(victim), GameOverReason.HP_ZERO));
        } else {
            long next = extraTurn ? userId : other;
            if (shells.isEmpty()) startRound(next);
            else startTurn(next);
        }
        return true;
    }

    /** @return 아이템을 썼으면 true (규칙상 못 쓰면 false, 에러를 보냄) */
    public synchronized boolean useItem(long userId, int slot) {
        if (!checkTurn(userId, PacketType.USE_ITEM)) return false;

        String[] mine = items.get(userId);
        if (slot < 0 || slot >= SLOT_COUNT || mine[slot] == null) {
            error(userId, "INVALID_SLOT", PacketType.USE_ITEM);
            return false;
        }
        String item = mine[slot];
        long other = other(userId);
        if (ItemType.HANDCUFFS.equals(item) && cuffed.get(other)) {
            error(userId, "INVALID_STATE", PacketType.USE_ITEM);
            return false;
        }
        if (ItemType.SAW.equals(item) && sawed) {
            error(userId, "INVALID_STATE", PacketType.USE_ITEM);
            return false;
        }

        mine[slot] = null;
        int myHp = hp.get(userId);
        long handcuffedUserId = 0;
        int handcuffTurns = 0;
        String beerShell = "";
        String revealedShell = null;

        switch (item) {
            case ItemType.MAGNIFIER -> {
                revealedShell = shells.peekFirst();
                sender.sendTo(userId, PacketType.SHELL_REVEALED, new ShellRevealedPacket(item, revealedShell));
            }
            case ItemType.CIGARETTE -> {
                myHp = Math.min(HP, myHp + 1);
                hp.put(userId, myHp);
            }
            case ItemType.SAW -> sawed = true;
            case ItemType.HANDCUFFS -> {
                cuffed.put(other, true);
                cuffSkipped.put(other, false);
                handcuffedUserId = other;
                handcuffTurns = 1;
            }
            case ItemType.BEER -> beerShell = shells.pollFirst();
            default -> { }
        }

        broadcast(PacketType.ITEM_USED, new DevItemUsedPacket(
                userId, slot, item, myHp, shells.size(), sawed, handcuffedUserId, handcuffTurns, beerShell));
        log.info("[dev] item room={} user={} item={} slot={}", users[0] + "v" + users[1], userId, item, slot);
        listener.itemUsed(userId, item, revealedShell, beerShell);

        if (ItemType.BEER.equals(item) && shells.isEmpty()) startRound(userId);
        return true;
    }

    /** 지급받은 아이템 중 다음 하나를 slot에 놓는다. 고른 칸은 상대에게만 알린다 (내 화면은 이미 놓았다). */
    public synchronized void placeItem(long userId, int slot) {
        Deque<String> remaining = toPlace.get(userId);
        if (over || !placing || remaining == null || remaining.isEmpty()) {
            error(userId, "INVALID_STATE", PacketType.PLACE_ITEM);   // 시간 초과로 서버가 이미 놓은 뒤 늦게 온 요청 등
            return;
        }
        if (slot < 0 || slot >= SLOT_COUNT || items.get(userId)[slot] != null) {
            error(userId, "INVALID_SLOT", PacketType.PLACE_ITEM);
            return;
        }

        place(userId, slot, false);
        if (allPlaced()) finishPlacing();
    }

    /** 사람이 아닌 자리(딜러)의 남은 아이템을 앞쪽 빈 칸부터 한꺼번에 놓는다. 상대에게 ITEM_PLACED로 알린다. */
    public synchronized void placeAllFor(long userId) {
        Deque<String> remaining = toPlace.get(userId);
        if (over || !placing || remaining == null) return;
        String[] slots = items.get(userId);
        for (int s = 0; s < SLOT_COUNT && !remaining.isEmpty(); s++)
            if (slots[s] == null) place(userId, s, false);
        if (allPlaced()) finishPlacing();
    }

    // ───────── 연승 모드(딜러)용 조회 ─────────

    synchronized boolean isOver() {
        return over;
    }

    synchronized long turnUserId() {
        return turnUserId;
    }

    synchronized int hpOf(long userId) {
        return hp.get(userId);
    }

    /** 칸 복사본 (빈 칸은 null). */
    synchronized String[] itemsOf(long userId) {
        return items.get(userId).clone();
    }

    synchronized boolean sawActive() {
        return sawed;
    }

    synchronized boolean isCuffed(long userId) {
        return cuffed.get(userId);
    }

    // ───────── 진행 ─────────

    // firstTurnUser: 첫 라운드는 무작위, 이후 라운드는 탄이 떨어진 턴의 다음 차례를 그대로 이어 간다
    private void startRound(long firstTurnUser) {
        for (long id : users) {
            cuffed.put(id, false);
            cuffSkipped.put(id, false);
        }

        if (round == 0) {   // 첫 라운드는 아이템 없음
            loadShells(firstTurnUser);
            return;
        }

        int count = GameRules.itemsPerGrant(grantCount);
        grantCount++;
        turnUserId = 0;   // 배치 중에는 누구의 턴도 아니다 (발사·아이템 사용 거절)
        placing = true;
        placingId++;
        nextFirstTurn = firstTurnUser;
        for (long id : users) grant(id, count);
        listener.placingStarted();

        if (allPlaced()) {   // 둘 다 칸이 꽉 차서 받은 게 없음
            finishPlacing();
            return;
        }
        int id = placingId;
        TIMER.schedule(() -> placeTimeout(id), PLACE_TIME_LIMIT_SEC, TimeUnit.SECONDS);
    }

    // 제한 시간: 남은 아이템을 앞쪽 빈 칸부터 놓고 둘 다에게 알린 뒤 장전으로 넘어간다
    private synchronized void placeTimeout(int id) {
        if (over || !placing || id != placingId) return;
        for (long userId : users) {
            String[] slots = items.get(userId);
            for (int s = 0; s < SLOT_COUNT && !toPlace.get(userId).isEmpty(); s++)
                if (slots[s] == null) place(userId, s, true);
        }
        log.info("[dev] place timeout room={}", users[0] + "v" + users[1]);
        finishPlacing();
    }

    private void place(long userId, int slot, boolean auto) {
        String item = toPlace.get(userId).pollFirst();
        items.get(userId)[slot] = item;
        int index = placedCount.merge(userId, 1, Integer::sum) - 1;
        var packet = new ItemPlacedPacket(userId, index, slot, item, auto);
        if (auto) broadcast(PacketType.ITEM_PLACED, packet);
        else sender.sendTo(other(userId), PacketType.ITEM_PLACED, packet);
    }

    private boolean allPlaced() {
        for (long id : users) if (!toPlace.get(id).isEmpty()) return false;
        return true;
    }

    private void finishPlacing() {
        placing = false;
        loadShells(nextFirstTurn);
    }

    private void loadShells(long firstTurnUser) {
        // 메인게임과 같은 고정 장전표 (GameRules, 탄이 꽂히는 순서만 무작위)
        int[] composition = GameRules.shellComposition(round);
        int blank = composition[0];
        int live = composition[1];
        List<String> list = new ArrayList<>();
        for (int i = 0; i < live; i++) list.add(ShellType.LIVE);
        for (int i = 0; i < blank; i++) list.add(ShellType.BLANK);
        Collections.shuffle(list, random);
        shells.clear();
        shells.addAll(list);

        round++;
        log.info("[dev] round {} shells(server only)={}", round, list);
        broadcast(PacketType.ROUND_START, new RoundStartPacket(round, live, blank));
        listener.roundStarted(live, blank);
        startTurn(firstTurnUser);
    }

    // 종류만 정해 보낸다 (slot -1, 칸은 플레이어가 PLACE_ITEM으로 고름). 빈 칸보다 많으면 남는 아이템은 버림
    private void grant(long userId, int count) {
        int free = 0;
        for (String s : items.get(userId)) if (s == null) free++;
        int n = Math.min(count, free);

        // 원작처럼 담배는 한 사람이 동시에 GameRules.MAX_CIGARETTES_HELD개까지만: 이미 가진 것 + 이번에 뽑은 것이 다 차면 후보에서 뺀다
        int cigarettes = 0;
        for (String s : items.get(userId)) if (ItemType.CIGARETTE.equals(s)) cigarettes++;

        Deque<String> types = new ArrayDeque<>();
        ItemSlot[] granted = new ItemSlot[n];
        for (int i = 0; i < n; i++) {
            String item;
            do {
                item = ITEM_POOL[random.nextInt(ITEM_POOL.length)];
            } while (ItemType.CIGARETTE.equals(item) && cigarettes >= GameRules.MAX_CIGARETTES_HELD);
            if (ItemType.CIGARETTE.equals(item)) cigarettes++;
            types.add(item);
            granted[i] = new ItemSlot(-1, item);
        }
        toPlace.put(userId, types);
        placedCount.put(userId, 0);
        broadcast(PacketType.ITEMS_GRANTED, new ItemsGrantedPacket(userId, granted, count - n));
    }

    // 수갑을 찬 사람의 첫 차례는 건너뛰고(상대에게 넘어감), 그다음 차례에 수갑을 부수고 진행
    private void startTurn(long userId) {
        long skipped = 0;
        if (cuffed.get(userId)) {
            if (!cuffSkipped.get(userId)) {
                cuffSkipped.put(userId, true);
                skipped = userId;
                userId = other(userId);
            } else {
                cuffed.put(userId, false);
                cuffSkipped.put(userId, false);
            }
        }

        turnUserId = userId;
        turnNumber++;
        broadcast(PacketType.TURN_START, new TurnStartPacket(userId, turnNumber, skipped, 0));
        listener.turnStarted(userId);
    }

    // ───────── 도우미 ─────────

    private boolean checkTurn(long userId, String requestType) {
        if (over || userId != turnUserId) {
            error(userId, "NOT_YOUR_TURN", requestType);
            return false;
        }
        return true;
    }

    private long other(long userId) {
        return userId == users[0] ? users[1] : users[0];
    }

    private void error(long userId, String code, String requestType) {
        sender.sendTo(userId, PacketType.ERROR, new ErrorPacket(code, code, requestType));
    }

    private void broadcast(String type, Object data) {
        for (long id : users) sender.sendTo(id, type, data);
    }

    /**
     * 방에서 일어난 일을 바깥(연승 모드)에 알린다. 모두 방의 잠금 안에서 불리므로 오래 걸리는 일은 하지 않는다
     * (딜러 행동은 다른 스레드로 예약한다). 기본은 아무것도 안 함 = 기존 1:1 방과 같다.
     */
    public interface Listener {

        Listener NONE = new Listener() { };

        /** 장전 직후 (실탄·공포탄 개수는 둘 다 아는 정보). */
        default void roundStarted(int live, int blank) { }

        /** 아이템을 지급하고 배치를 기다리기 시작했다. */
        default void placingStarted() { }

        /** 차례가 시작됐다 (수갑으로 건너뛴 사람이 아니라 실제로 행동할 사람). */
        default void turnStarted(long userId) { }

        default void fired(long shooterId, long victimId, String shell, int damage) { }

        /** revealedShell: 돋보기로 본 탄 (돋보기가 아니면 null). ejectedShell: 맥주로 뺀 탄 (아니면 ""). */
        default void itemUsed(long userId, String item, String revealedShell, String ejectedShell) { }

        /** GAME_OVER로 보낼 내용. 연승 모드는 여기서 기록을 저장하고 연승·경험치를 담는다. */
        default Object gameOver(long winnerId, String reason) {
            return new GameOverPacket(winnerId, reason, new PlayerResult[0], false);
        }
    }

    /** ITEM_USED + 맥주로 뺀 탄 종류. 정식 규격(PacketSpec 5.5)에는 없는 개발용 필드. */
    public record DevItemUsedPacket(long userId, int slot, String itemType, int hp, int remainingShells, boolean sawActive,
                                    long handcuffedUserId, int handcuffTurns, String shell) {
    }
}
