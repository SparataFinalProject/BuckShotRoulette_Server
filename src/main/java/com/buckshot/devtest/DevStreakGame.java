package com.buckshot.devtest;

import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import com.buckshot.game.ai.DealerAction;
import com.buckshot.game.ai.DealerBrain;
import com.buckshot.game.ai.DealerMemory;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.HardDealerBrain;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.service.StreakService;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.ShellType;
import com.buckshot.ws.packet.Target;
import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.s2c.RankedGameOverPacket;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 연승 모드 한 판: DevGameRoom의 한 자리를 딜러 AI가 맡고, 사람 쪽 기록(턴·사격·피해·아이템)을 모아 판이 끝나면 저장한다.
 *
 * <p>딜러는 사람과 같은 정보만 본다: 남은 실탄·공포탄 개수(공개), 딜러가 돋보기로 본 탄. 탄 순서는 보지 않는다.
 * 방의 잠금 안에서 불리는 콜백은 딜러 행동을 바로 하지 않고 다른 스레드에 예약한다 (재귀 호출과 연출 겹침을 피하려고).
 */
@Slf4j
class DevStreakGame implements DevGameRoom.Listener {

    static final long DEALER_ID = -1L;
    static final String MODE = "RANKED_SOLO";
    private static final int MAX_ITEMS_PER_TURN = 8;     // 무한 반복 방지
    private static final long ACTION_DELAY_MS = 900;    // 딜러가 생각하는 시간 (클라 연출과 겹치지 않게)
    private static final long PLACE_DELAY_MS = 1500;
    private static final ScheduledExecutorService DEALER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dev-dealer");
        t.setDaemon(true);
        return t;
    });

    private final long userId;
    private final StreakService streakService;
    private final DealerBrain brain = new HardDealerBrain();
    private final DealerMemory memory = new DealerMemory();
    private DevGameRoom room;

    // 딜러
    private int scheduledStep;        // 마지막으로 예약한 행동 번호 (예약이 겹치면 마지막 것만 실행)
    private int dealerItemsThisTurn;

    // 사람 쪽 기록 (GameSummary). 턴 = 사람이 실제로 행동한 차례 수
    private int turns;
    private int shots;
    private int correctShots;
    private int damageTaken;
    private final Map<Item, Integer> itemUsage = new EnumMap<>(Item.class);

    DevStreakGame(long userId, StreakService streakService) {
        this.userId = userId;
        this.streakService = streakService;
    }

    void attach(DevGameRoom room) {
        this.room = room;
    }

    // ───────── 방에서 오는 알림 (방 잠금 안) ─────────

    @Override
    public void roundStarted(int live, int blank) {
        memory.onLoaded(live, blank);
    }

    @Override
    public void placingStarted() {
        DEALER.schedule(() -> room.placeAllFor(DEALER_ID), PLACE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void turnStarted(long turnUserId) {
        if (turnUserId == userId) {
            turns++;
            return;
        }
        dealerItemsThisTurn = 0;
        scheduleDealer();
    }

    @Override
    public void fired(long shooterId, long victimId, String shell, int damage) {
        memory.onShellRemoved(Shell.valueOf(shell));
        if (shooterId == userId) {
            shots++;
            boolean self = victimId == shooterId;
            if (self == ShellType.BLANK.equals(shell)) correctShots++;   // 나+공포탄 또는 상대+실탄
        }
        if (victimId == userId) damageTaken += damage;
    }

    @Override
    public void itemUsed(long user, String item, String revealedShell, String ejectedShell) {
        if (ejectedShell != null && !ejectedShell.isEmpty()) memory.onShellRemoved(Shell.valueOf(ejectedShell));
        if (user == userId) {
            itemUsage.merge(Item.valueOf(item), 1, Integer::sum);
            return;
        }
        if (ItemType.MAGNIFIER.equals(item) && revealedShell != null) memory.onPeeked(Shell.valueOf(revealedShell));
    }

    @Override
    public Object gameOver(long winnerId, String reason) {
        GameResult result = winnerId == userId ? GameResult.WIN : GameResult.LOSE;
        GameSummary summary = new GameSummary(turns, shots, correctShots, damageTaken, room.hpOf(userId), new EnumMap<>(itemUsage));
        try {
            StreakResult r = streakService.onGameEnd(userId, result, summary);
            log.info("[streak] game over userId={} result={} streak={} best={} exp+{}", userId, result, r.streak(), r.bestStreak(), r.expGained());
            return new RankedGameOverPacket(winnerId, reason, new PlayerResult[0], true,
                    MODE, r.streak(), r.bestStreak(), r.isNewBest(), r.expGained());
        } catch (RuntimeException e) {
            log.warn("[streak] record failed userId={}: {}", userId, e.getMessage(), e);
            return new RankedGameOverPacket(winnerId, reason, new PlayerResult[0], false, MODE, 0, 0, false, 0);
        }
    }

    // ───────── 딜러 행동 (dev-dealer 스레드) ─────────

    private void scheduleDealer() {
        int step = ++scheduledStep;
        DEALER.schedule(() -> dealerStep(step), ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // 한 번에 행동 하나. 아이템을 써서 차례가 이어지면 다음 행동을 다시 예약한다
    private void dealerStep(int step) {
        synchronized (room) {
            if (step != scheduledStep || room.isOver() || room.turnUserId() != DEALER_ID) return;
            try {
                act(decide());
            } catch (RuntimeException e) {
                log.warn("[streak] dealer failed, shoot instead: {}", e.getMessage(), e);
                room.fire(DEALER_ID, Target.OPPONENT);
            }
            if (!room.isOver() && room.turnUserId() == DEALER_ID) scheduleDealer();
        }
    }

    private DealerAction decide() {
        DealerObservation observation = observe();
        if (dealerItemsThisTurn >= MAX_ITEMS_PER_TURN) {
            return observation.liveChance() < 0.5 ? DealerAction.SHOOT_SELF : DealerAction.SHOOT_OPPONENT;
        }
        return brain.decide(observation);
    }

    private void act(DealerAction action) {
        switch (action.kind()) {
            case SHOOT_OPPONENT -> room.fire(DEALER_ID, Target.OPPONENT);
            case SHOOT_SELF -> room.fire(DEALER_ID, Target.SELF);
            case USE_ITEM -> {
                dealerItemsThisTurn++;
                int slot = slotOf(action.item());
                if (slot < 0 || !room.useItem(DEALER_ID, slot)) {   // 판단이 방 규칙과 어긋나면 그냥 쏜다 (멈추지 않게)
                    log.warn("[streak] dealer could not use {}, shooting", action.item());
                    room.fire(DEALER_ID, observe().liveChance() < 0.5 ? Target.SELF : Target.OPPONENT);
                }
            }
        }
    }

    // 딜러가 볼 수 있는 것만으로 관찰을 만든다 (DealerMemory.observe와 같은 규칙, 방의 문자열 상태에서)
    private DealerObservation observe() {
        Set<Item> usable = EnumSet.noneOf(Item.class);
        for (String s : room.itemsOf(DEALER_ID)) {
            if (s == null) continue;
            Item item = Item.valueOf(s);
            if (item == Item.SAW && room.sawActive()) continue;
            if (item == Item.HANDCUFFS && room.isCuffed(userId)) continue;
            usable.add(item);
        }
        return new DealerObservation(room.hpOf(DEALER_ID), room.hpOf(userId), GameRules.MAX_HP,
                memory.liveCount(), memory.blankCount(), memory.knownShell(),
                room.sawActive(), room.isCuffed(userId), usable);
    }

    private int slotOf(Item item) {
        String[] slots = room.itemsOf(DEALER_ID);
        for (int s = 0; s < slots.length; s++) if (item.name().equals(slots[s])) return s;
        return -1;
    }
}
