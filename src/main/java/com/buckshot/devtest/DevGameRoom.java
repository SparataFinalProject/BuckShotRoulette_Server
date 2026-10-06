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
import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 임시 게임 방: 두 클라이언트가 한 판을 끝까지 진행해 보는 용도.
 * 규칙은 클라이언트의 MultiMockInGameClient와 같다 (원작 규칙: 체력 6, 수갑은 한 번 건너뛰고 다음 차례에 부숨, 이미 찬 상대에겐 못 씀(중첩 불가),
 * 쇠톱은 다음 한 발 피해 2, 첫 라운드는 아이템 없음, 다시 장전하면 수갑이 풀림). 게임 중 한쪽이 나가면 남은 쪽 승리(DISCONNECT).
 * 패킷 순서: (아이템 지급 ×2) -> ROUND_START -> TURN_START. 맥주로 뺀 탄은 ITEM_USED.shell로 둘 다에게 공개한다 (DevItemUsedPacket).
 * 정식 GameRoom(ServerDesign 6.4)과 팀원의 game 규칙 코어가 연결되면 지운다. 방 단위 synchronized로 두 플레이어 요청을 직렬화한다.
 */
@Slf4j
public class DevGameRoom {

    static final int HP = GameRules.MAX_HP;
    private static final int SLOT_COUNT = GameRules.MAX_ITEM_SLOTS;
    private static final String[] ITEM_POOL = {
            ItemType.MAGNIFIER, ItemType.BEER, ItemType.CIGARETTE, ItemType.SAW, ItemType.HANDCUFFS };

    private final PacketSender sender;
    private final long[] users;
    private final Random random = new Random();

    private final Deque<String> shells = new ArrayDeque<>();   // 앞이 약실
    private final Map<Long, Integer> hp = new HashMap<>();
    private final Map<Long, String[]> items = new HashMap<>();
    private final Map<Long, Boolean> cuffed = new HashMap<>();
    private final Map<Long, Boolean> cuffSkipped = new HashMap<>();
    private boolean sawed;
    private long turnUserId;
    private int round;
    private int turnNumber;
    private int grantCount;
    private boolean over;

    public DevGameRoom(PacketSender sender, long userA, long userB) {
        this.sender = sender;
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
        startRound();
    }

    /** 게임 중 한쪽이 나가면(연결 끊김) 남은 쪽이 바로 승리한다. 이미 끝난 게임이면 아무것도 하지 않는다. */
    public synchronized void disconnect(long leftUserId) {
        if (over) return;
        over = true;
        long winner = other(leftUserId);
        log.info("[dev] disconnect room={}v{} left={} winner={}", users[0], users[1], leftUserId, winner);
        sender.sendTo(winner, PacketType.GAME_OVER,
                new GameOverPacket(winner, GameOverReason.DISCONNECT, new PlayerResult[0], false));
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

    public synchronized void fire(long userId, String target) {
        if (!checkTurn(userId, PacketType.FIRE)) return;
        if (!Target.SELF.equals(target) && !Target.OPPONENT.equals(target)) {
            error(userId, "INVALID_MESSAGE", PacketType.FIRE);
            return;
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

        if (victimHp <= 0) {
            over = true;
            broadcast(PacketType.GAME_OVER,
                    new GameOverPacket(other(victim), GameOverReason.HP_ZERO, new PlayerResult[0], false));
        } else if (shells.isEmpty()) {
            startRound();
        } else {
            startTurn(extraTurn ? userId : other);
        }
    }

    public synchronized void useItem(long userId, int slot) {
        if (!checkTurn(userId, PacketType.USE_ITEM)) return;

        String[] mine = items.get(userId);
        if (slot < 0 || slot >= SLOT_COUNT || mine[slot] == null) {
            error(userId, "INVALID_SLOT", PacketType.USE_ITEM);
            return;
        }
        String item = mine[slot];
        long other = other(userId);
        if (ItemType.HANDCUFFS.equals(item) && cuffed.get(other)) {
            error(userId, "INVALID_STATE", PacketType.USE_ITEM);
            return;
        }
        if (ItemType.SAW.equals(item) && sawed) {
            error(userId, "INVALID_STATE", PacketType.USE_ITEM);
            return;
        }

        mine[slot] = null;
        int myHp = hp.get(userId);
        long handcuffedUserId = 0;
        int handcuffTurns = 0;
        String beerShell = "";

        switch (item) {
            case ItemType.MAGNIFIER ->
                sender.sendTo(userId, PacketType.SHELL_REVEALED, new ShellRevealedPacket(item, shells.peekFirst()));
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

        if (ItemType.BEER.equals(item) && shells.isEmpty()) startRound();
    }

    // ───────── 진행 ─────────

    private void startRound() {
        for (long id : users) {
            cuffed.put(id, false);
            cuffSkipped.put(id, false);
        }

        if (round > 0) {
            int count = GameRules.itemsPerGrant(grantCount);
            grantCount++;
            for (long id : users) grant(id, count);
        }

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
        startTurn(users[random.nextInt(2)]);
    }

    // 빈 칸에 무작위로 놓는다 (칸이 모자라면 남는 아이템은 버림)
    private void grant(long userId, int count) {
        String[] slots = items.get(userId);
        List<Integer> free = new ArrayList<>();
        for (int s = 0; s < SLOT_COUNT; s++) if (slots[s] == null) free.add(s);
        Collections.shuffle(free, random);

        List<ItemSlot> granted = new ArrayList<>();
        for (int i = 0; i < count && i < free.size(); i++) {
            String item = ITEM_POOL[random.nextInt(ITEM_POOL.length)];
            slots[free.get(i)] = item;
            granted.add(new ItemSlot(free.get(i), item));
        }
        broadcast(PacketType.ITEMS_GRANTED,
                new ItemsGrantedPacket(userId, granted.toArray(new ItemSlot[0]), Math.max(0, count - free.size())));
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

    /** ITEM_USED + 맥주로 뺀 탄 종류. 정식 규격(PacketSpec 5.5)에는 없는 개발용 필드. */
    public record DevItemUsedPacket(long userId, int slot, String itemType, int hp, int remainingShells, boolean sawActive,
                                    long handcuffedUserId, int handcuffTurns, String shell) {
    }
}
