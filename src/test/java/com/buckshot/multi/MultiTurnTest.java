package com.buckshot.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.ItemPhaseBegin;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 차례 진행(총 집기, 대상 고르기, 사격, 체력, 라운드 종료). 원작 고정 시간은 짧게 줄여서 돌린다 */
class MultiTurnTest {

    private record Sent(long userId, String type, Object data) {
    }

    private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());
    private MultiGameService game;

    @BeforeEach
    void setUp() {
        PacketSender sender = mock(PacketSender.class);
        doAnswer(inv -> {
            sent.add(new Sent(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
            return null;
        }).when(sender).sendTo(anyLong(), anyString(), any());
        game = new MultiGameService(sender);
        game.timing = new MultiGameService.Timing(0.05, 0.02, 1, 1, 0.1, 0.05, 0.3, 0.1, 0.02, 0.05);
        game.create(List.of(new Member(1, "a"), new Member(2, "b"), new Member(3, "c")));
    }

    /** 사용자 userId가 받은 type 패킷 중 조건에 맞는 첫 것 (from 번째부터). 없으면 4초까지 기다린다 */
    private <T> T await(long userId, String type, Class<T> cls, Predicate<T> match) {
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) {
            synchronized (sent) {
                for (Sent s : sent) {
                    if (s.userId == userId && s.type.equals(type) && cls.isInstance(s.data) && match.test(cls.cast(s.data))) return cls.cast(s.data);
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    /** userId가 받은 ITEM_GRABBED 중 seat가 꺼낸 것의 수 */
    private int grabsBy(long userId, int seat) {
        synchronized (sent) {
            return (int) sent.stream().filter(s -> s.userId == userId && s.data instanceof MultiPackets.ItemGrabbed g && g.seat() == seat).count();
        }
    }

    private <T> int count(long userId, String type, Class<T> cls) {
        synchronized (sent) {
            return (int) sent.stream().filter(s -> s.userId == userId && s.type.equals(type) && cls.isInstance(s.data)).count();
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 아이템 받기까지 끝내고 첫 차례가 시작될 때까지 간다. 첫 차례 자리를 돌려준다 */
    private int startTurns() {
        for (long u = 1; u <= 3; u++) game.ready(u);
        for (long u = 1; u <= 3; u++) game.introDone(u);
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, i);
            }
        }
        LoadShotgun load = await(1, PacketType.MULTI_LOAD_SHOTGUN, LoadShotgun.class, x -> true);
        assertNotNull(load, "장전 패킷이 안 왔다");
        sleep(200);   // loadToTurn(0.05초) 뒤 첫 차례가 시작된다
        return load.firstTurnSeat();
    }

    private static long user(int seat) {
        return seat + 1L;   // 자리 번호 = 참가 순서
    }

    @Test
    @DisplayName("차례인 사람만 총을 집을 수 있고, 모두에게 알린다")
    void onlyCurrentTurnPicksUp() {
        int first = startTurns();
        int other = (first + 1) % 3;
        game.pickupShotgun(user(other));
        sleep(100);
        assertEquals(0, count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class));

        game.pickupShotgun(user(first));
        for (long u = 1; u <= 3; u++) {
            ShotgunPicked p = await(u, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class, x -> true);
            assertNotNull(p);
            assertEquals(first, p.seat());
        }
    }

    @Test
    @DisplayName("총을 든 동안 고르는 쪽은 보낸 사람 빼고 나머지에게만 간다")
    void lookIsRelayedToOthers() {
        int first = startTurns();
        game.pickupShotgun(user(first));
        game.look(user(first), "left");
        game.look(user(first), "up");   // 잘못된 방향은 버린다
        LookAt got = await(user((first + 1) % 3), PacketType.MULTI_LOOK_AT, LookAt.class, x -> true);
        assertNotNull(got);
        assertEquals(first, got.seat());
        assertEquals("left", got.direction());
        sleep(50);
        assertEquals(1, count(user((first + 1) % 3), PacketType.MULTI_LOOK_AT, LookAt.class));
        assertEquals(0, count(user(first), PacketType.MULTI_LOOK_AT, LookAt.class));
    }

    @Test
    @DisplayName("실탄이면 체력이 줄고 차례가 넘어간다 (공포탄이면 체력 그대로)")
    void shotChangesHealthAndPassesTurn() {
        int first = startTurns();
        int target = (first + 1) % 3;
        game.pickupShotgun(user(first));
        game.shoot(user(first), target);
        Shot shot = await(user(target), PacketType.MULTI_SHOT, Shot.class, x -> true);
        assertNotNull(shot);
        assertEquals(first, shot.shooter());
        assertEquals(target, shot.target());
        assertEquals(shot.live() ? MultiGameService.STARTING_HEALTH - 1 : MultiGameService.STARTING_HEALTH, shot.health()[target]);
        assertTrue(shot.endsTurn());
        Turn turn = await(user(target), PacketType.MULTI_TURN, Turn.class, x -> true);
        assertNotNull(turn);
        assertEquals(target, turn.seat());
        assertEquals("next", turn.reason());
    }

    @Test
    @DisplayName("자기를 공포탄으로 쏘면 차례가 그대로이고, 실탄이면 넘어간다")
    void selfShot() {
        int first = startTurns();
        game.setChamberForTest(user(first), false, true, true);
        game.pickupShotgun(user(first));
        game.shoot(user(first), first);
        Shot blank = await(1, PacketType.MULTI_SHOT, Shot.class, x -> true);
        assertFalse(blank.live());
        assertFalse(blank.endsTurn());
        assertEquals(2, blank.shellsLeft());
        Turn keep = await(1, PacketType.MULTI_TURN, Turn.class, x -> true);
        assertEquals(first, keep.seat());
        assertEquals("keep", keep.reason());

        game.pickupShotgun(user(first));
        game.shoot(user(first), first);
        Shot live = await(1, PacketType.MULTI_SHOT, Shot.class, x -> x.live());
        assertEquals(MultiGameService.STARTING_HEALTH - 1, live.health()[first]);
        assertTrue(live.endsTurn());
        Turn next = await(1, PacketType.MULTI_TURN, Turn.class, x -> x.reason().equals("next"));
        assertEquals((first + 1) % 3, next.seat());
    }

    @Test
    @DisplayName("집지 않고 제한 시간이 지나면 다음 사람에게 넘어간다")
    void turnTimeout() {
        int first = startTurns();
        Turn turn = await(1, PacketType.MULTI_TURN, Turn.class, x -> true);
        assertNotNull(turn);
        assertEquals((first + 1) % 3, turn.seat());
        assertEquals("timeout", turn.reason());
    }

    @Test
    @DisplayName("대상을 고르지 못하면 총을 내려놓고 다음 사람에게 넘어간다")
    void targetTimeout() {
        int first = startTurns();
        game.pickupShotgun(user(first));
        ShotgunDropped dropped = await(1, PacketType.MULTI_SHOTGUN_DROPPED, ShotgunDropped.class, x -> true);
        assertNotNull(dropped);
        assertEquals(first, dropped.seat());
        Turn turn = await(1, PacketType.MULTI_TURN, Turn.class, x -> true);
        assertEquals((first + 1) % 3, turn.seat());
        // 내려놓은 뒤에는 쏠 수 없다
        game.shoot(user(first), (first + 1) % 3);
        sleep(100);
        assertEquals(0, count(1, PacketType.MULTI_SHOT, Shot.class));
    }

    @Test
    @DisplayName("총을 안 든 사람이나 죽은 사람은 쏠 수 없다")
    void invalidShots() {
        int first = startTurns();
        game.shoot(user(first), (first + 1) % 3);   // 집기 전
        game.pickupShotgun(user(first));
        game.shoot(user((first + 1) % 3), first);   // 차례가 아닌 사람
        game.shoot(user(first), 7);                 // 없는 자리
        game.shoot(user(first), -1);
        sleep(100);
        assertEquals(0, count(1, PacketType.MULTI_SHOT, Shot.class));
    }

    @Test
    @DisplayName("상한 약(7)은 나오지 않고, 재머는 테이블 전체에 하나까지")
    void noExpiredMedicine() {
        for (long u = 1; u <= 3; u++) game.ready(u);
        for (long u = 1; u <= 3; u++) game.introDone(u);
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, i);
            }
        }
        int jammers = 0;
        int coins = 0;
        synchronized (sent) {
            for (Sent s : sent) {
                if (s.userId == 1 && s.data instanceof MultiPackets.ItemGrabbed g) {
                    assertTrue(g.itemId() >= 1 && g.itemId() <= 11 && g.itemId() != 7, "나오면 안 되는 아이템 " + g.itemId());
                    if (g.itemId() == MultiGameService.JAMMER) jammers++;
                    if (g.itemId() == MultiGameService.GAMBLE_COIN) coins++;
                }
            }
        }
        assertTrue(jammers <= 1, "재머가 " + jammers + "개");
        assertTrue(coins <= 1, "도박 동전이 " + coins + "개");
    }

    @Test
    @DisplayName("도박 동전은 아이템 받기를 여러 번 해도 테이블에 하나까지만 나온다")
    void coinAtMostOneOnTable() {
        for (int round = 0; round < 60; round++) {
            List<Sent> log = Collections.synchronizedList(new ArrayList<>());
            PacketSender sender = mock(PacketSender.class);
            doAnswer(inv -> {
                log.add(new Sent(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
                return null;
            }).when(sender).sendTo(anyLong(), anyString(), any());
            MultiGameService g = new MultiGameService(sender);
            g.create(List.of(new Member(1, "a"), new Member(2, "b"), new Member(3, "c"), new Member(4, "d")));
            for (long u = 1; u <= 4; u++) g.ready(u);
            for (long u = 1; u <= 4; u++) g.introDone(u);
            // 모두 꺼내 들기만 하고 놓기 전에 다른 사람이 또 꺼내도 동전이 둘이 되면 안 된다
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                for (long u = 1; u <= 4; u++) g.grabItem(u);
                for (long u = 1; u <= 4; u++) g.placeItem(u, i);
            }
            long coins;
            synchronized (log) {
                coins = log.stream().filter(s -> s.userId == 1 && s.data instanceof MultiPackets.ItemGrabbed x && x.itemId() == MultiGameService.GAMBLE_COIN).count();
            }
            assertTrue(coins <= 1, "도박 동전이 " + coins + "개");
        }
    }

    @Test
    @DisplayName("도박 동전: 칸을 전부 비우고 같은 개수를 상자에서 다시 받는다 (동전은 다시 나오지 않고, 다른 사람은 못 받는다). 끝나면 차례가 이어진다")
    void gambleCoinRefillsItems() {
        int first = startTurns();
        int other = (first + 1) % 3;
        for (int i = 0; i < 4; i++) game.setItemForTest(user(first), i, 4);   // 담배 4개
        game.setItemForTest(user(first), 7, MultiGameService.GAMBLE_COIN);
        game.useItem(user(first), 7);
        MultiPackets.ItemUsed used = await(user(other), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.GAMBLE_COIN);
        assertNotNull(used, "동전 사용 패킷이 안 왔다");
        assertEquals(first, used.seat());
        assertEquals(4, used.refill());
        int grabbedBefore = grabsBy(user(other), first);
        int otherBefore = count(user(first), PacketType.MULTI_ITEM_GRABBED, MultiPackets.ItemGrabbed.class);
        sleep(300);   // 연출 시간(4.8초 x 0.02) 뒤 다시 받기가 시작된다
        game.grabItem(user(other));   // 차례가 아닌 사람은 못 받는다
        assertEquals(otherBefore, count(user(first), PacketType.MULTI_ITEM_GRABBED, MultiPackets.ItemGrabbed.class));
        for (int i = 0; i < 4; i++) {
            game.grabItem(user(first));
            game.placeItem(user(first), i);
        }
        List<MultiPackets.ItemGrabbed> grabs = new ArrayList<>();
        synchronized (sent) {
            int seen = 0;
            for (Sent s : sent) {
                if (s.userId == user(other) && s.data instanceof MultiPackets.ItemGrabbed g && g.seat() == first) {
                    if (++seen > grabbedBefore) grabs.add(g);
                }
            }
        }
        assertEquals(4, grabs.size());
        for (MultiPackets.ItemGrabbed g : grabs) assertTrue(g.itemId() != MultiGameService.GAMBLE_COIN, "다시 받는데 동전이 나왔다");
        assertNotNull(await(user(other), PacketType.MULTI_ITEM_PHASE_DONE, MultiPackets.ItemPhaseDone.class, x -> x.seat() == first && !x.timedOut()));
        sleep(100);
        game.pickupShotgun(user(first));   // 차례가 이어져 총을 집을 수 있다
        assertNotNull(await(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class, x -> x.seat() == first));
    }

    @Test
    @DisplayName("도박 동전: 돌려보낼 다른 아이템이 없으면 쓸 수 없다")
    void gambleCoinNeedsOtherItems() {
        int first = startTurns();
        for (int i = 0; i < 8; i++) game.setItemForTest(user(first), i, 0);
        game.setItemForTest(user(first), 7, MultiGameService.GAMBLE_COIN);
        game.useItem(user(first), 7);
        sleep(100);
        assertEquals(0, count(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class));
    }

    @Test
    @DisplayName("재머: 고른 사람은 다음 차례를 한 번 넘기고(jammed), 그다음에 풀린다(unjammed)")
    void jammerSkipsTargetOnce() {
        int first = startTurns();
        int target = (first + 1) % 3;
        int after = (first + 2) % 3;
        game.setItemForTest(user(first), 7, MultiGameService.JAMMER);
        game.useItem(user(first), 7);
        assertNotNull(await(user(first), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.JAMMER));
        game.itemSecondary(user(first), first, 0);   // 자기는 고를 수 없다
        sleep(30);
        assertEquals(0, count(1, PacketType.MULTI_ITEM_SECONDARY_DONE, MultiPackets.ItemSecondaryDone.class));
        game.itemSecondary(user(first), target, 0);
        MultiPackets.ItemSecondaryDone done = await(1, PacketType.MULTI_ITEM_SECONDARY_DONE, MultiPackets.ItemSecondaryDone.class, x -> true);
        assertNotNull(done);
        assertEquals(target, done.target());
        assertFalse(done.timedOut());
        // 차례가 이어지다가 제한 시간이 지나면 걸린 사람은 넘기고 그다음 사람에게
        Turn skip = await(1, PacketType.MULTI_TURN, Turn.class, x -> x.seat() == target);
        assertNotNull(skip);
        assertEquals("jammed", skip.reason());
        assertEquals(0, skip.timeoutSec());
        Turn next = await(1, PacketType.MULTI_TURN, Turn.class, x -> x.seat() == after);
        assertNotNull(next);
        // 한 바퀴 돌아 다시 오면 풀린다
        Turn unjam = await(1, PacketType.MULTI_TURN, Turn.class, x -> x.seat() == target && x.reason().equals("unjammed"));
        assertNotNull(unjam, "풀리지 않았다");
    }

    @Test
    @DisplayName("재머: 고르지 않으면 제한 시간 뒤 내려놓고 차례가 이어진다")
    void jammerTimeout() {
        int first = startTurns();
        game.setItemForTest(user(first), 7, MultiGameService.JAMMER);
        game.useItem(user(first), 7);
        MultiPackets.ItemSecondaryDone done = await(1, PacketType.MULTI_ITEM_SECONDARY_DONE, MultiPackets.ItemSecondaryDone.class, x -> true);
        assertNotNull(done);
        assertTrue(done.timedOut());
        assertEquals(-1, done.target());
        sleep(100);
        game.pickupShotgun(user(first));   // 차례가 이어진다
        assertNotNull(await(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class, x -> x.seat() == first));
    }

    @Test
    @DisplayName("아드레날린: 다른 사람의 아이템을 훔쳐 바로 쓰고, 아드레날린은 훔칠 수 없다")
    void adrenalineSteals() {
        int first = startTurns();
        int victim = (first + 1) % 3;
        game.setItemForTest(user(victim), 2, 4);
        game.setItemForTest(user(victim), 3, MultiGameService.ADRENALINE);
        game.setItemForTest(user(first), 7, MultiGameService.ADRENALINE);
        game.useItem(user(first), 7);
        assertNotNull(await(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.ADRENALINE));
        game.itemSecondary(user(first), victim, 3);   // 아드레날린
        game.itemSecondary(user(first), first, 0);    // 자기 칸
        sleep(30);
        assertEquals(1, count(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class));
        game.itemSecondary(user(first), victim, 2);
        MultiPackets.ItemUsed stolen = await(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, MultiPackets.ItemUsed::stolen);
        assertNotNull(stolen);
        assertEquals(first, stolen.seat());
        assertEquals(victim, stolen.fromSeat());
        assertEquals(2, stolen.cell());
        assertEquals(4, stolen.itemId());
    }

    @Test
    @DisplayName("아드레날린: 고르지 않으면 제한 시간 뒤 끝나고 차례가 이어진다")
    void adrenalineTimeout() {
        int first = startTurns();
        game.setItemForTest(user((first + 1) % 3), 2, 4);
        game.setItemForTest(user(first), 7, MultiGameService.ADRENALINE);
        game.useItem(user(first), 7);
        MultiPackets.ItemSecondaryDone done = await(1, PacketType.MULTI_ITEM_SECONDARY_DONE, MultiPackets.ItemSecondaryDone.class, x -> true);
        assertNotNull(done);
        assertEquals(MultiGameService.ADRENALINE, done.itemId());
        assertTrue(done.timedOut());
    }

    @Test
    @DisplayName("인버터: 첫 셸이 반대로 바뀐다")
    void inverterFlips() {
        int first = startTurns();
        game.setItemForTest(user(first), 7, MultiGameService.INVERTER);
        game.setChamberForTest(user(first), true, false);
        game.useItem(user(first), 7);
        assertNotNull(await(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.INVERTER));
        sleep(150);
        game.pickupShotgun(user(first));
        game.shoot(user(first), first);
        Shot shot = await(1, PacketType.MULTI_SHOT, Shot.class, x -> true);
        assertFalse(shot.live());
    }

    @Test
    @DisplayName("리모컨: 턴 방향이 반대로 바뀐다")
    void remoteReverses() {
        int first = startTurns();
        game.setItemForTest(user(first), 7, MultiGameService.REMOTE);
        game.useItem(user(first), 7);
        MultiPackets.ItemUsed used = await(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.REMOTE);
        assertFalse(used.turnCw());
        Turn next = await(1, PacketType.MULTI_TURN, Turn.class, x -> true);   // 제한 시간이 지나 넘어간다
        assertEquals((first + 2) % 3, next.seat());
    }

    @Test
    @DisplayName("대포폰: 세 번째 이후 셸 하나를 쓴 사람만 알고, 셸이 2개 이하면 알려 주지 못한다")
    void burnerPhone() {
        int first = startTurns();
        int other = (first + 1) % 3;
        game.setItemForTest(user(first), 6, MultiGameService.BURNER_PHONE);
        game.setItemForTest(user(first), 7, MultiGameService.BURNER_PHONE);
        boolean[] chamber = {true, true, false, true};
        game.setChamberForTest(user(first), chamber);
        game.useItem(user(first), 6);
        MultiPackets.ItemUsed mine = await(user(first), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.BURNER_PHONE);
        MultiPackets.ItemUsed theirs = await(user(other), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == MultiGameService.BURNER_PHONE);
        assertTrue(mine.phoneIndex() == 3 || mine.phoneIndex() == 4, "순번 " + mine.phoneIndex());
        assertEquals(chamber[mine.phoneIndex() - 1] ? 1 : 0, mine.shell());
        assertEquals(0, theirs.phoneIndex());
        assertEquals(-1, theirs.shell());
        sleep(150);
        game.setChamberForTest(user(first), true, false);
        game.useItem(user(first), 7);
        MultiPackets.ItemUsed unfortunate = nth(PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, user(first) == 1 ? 1 : 0);
        if (user(first) == 1) assertEquals(-1, unfortunate.phoneIndex());
    }

    @Test
    @DisplayName("대포폰 순번: 셸 3개 이상이면 3~셸 수(최대 7)")
    void phoneIndexRange() {
        java.util.Random random = new java.util.Random(1);
        assertEquals(-1, MultiGameService.phoneIndex(2, random));
        for (int i = 0; i < 200; i++) {
            int n = 3 + random.nextInt(6);
            int index = MultiGameService.phoneIndex(n, random);
            assertTrue(index >= 3 && index <= Math.min(n, 7), "셸 " + n + "개에서 " + index);
        }
    }

    @Test
    @DisplayName("턴 방향 CCW면 자리 번호가 작은 쪽으로")
    void nextTurnCcw() {
        assertEquals(2, MultiGameService.nextTurn(0, new boolean[] {true, true, true}, false));
        assertEquals(0, MultiGameService.nextTurn(1, new boolean[] {true, true, true}, false));
        assertEquals(1, MultiGameService.nextTurn(3, new boolean[] {true, true, false, true}, false));
    }

    @Test
    @DisplayName("쇠톱: 다음 실탄 피해 2, 이미 잘렸으면 또 못 쓰고, 쏘고 나면 원래대로")
    void handsawDoublesDamage() {
        int first = startTurns();
        int target = (first + 1) % 3;
        game.setItemForTest(user(first), 0, 1);
        game.setItemForTest(user(first), 1, 1);
        game.setChamberForTest(user(first), true, true);
        game.useItem(user(first), 0);
        MultiPackets.ItemUsed used = nth(PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, 0);
        assertEquals(1, used.itemId());
        assertTrue(used.sawed());
        game.pickupShotgun(user(first));   // 쓰는 동안은 받지 않는다
        sleep(30);
        assertEquals(0, count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class));
        sleep(150);   // 쓰기가 끝날 때까지 (짧게 줄인 시간)
        game.useItem(user(first), 1);   // 이미 잘렸다
        sleep(30);
        assertEquals(1, count(1, PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class));
        game.pickupShotgun(user(first));
        game.shoot(user(first), target);
        Shot shot = nth(PacketType.MULTI_SHOT, Shot.class, 0);
        assertTrue(shot.live());
        assertTrue(shot.sawed());
        assertEquals(MultiGameService.STARTING_HEALTH - 2, shot.health()[target]);
    }

    @Test
    @DisplayName("돋보기: 첫 셸은 쓴 사람만 알고, 맥주: 첫 셸을 빼서 모두에게 보여 준다")
    void magnifierAndBeer() {
        int first = startTurns();
        int other = (first + 1) % 3;
        game.setItemForTest(user(first), 0, 2);
        game.setItemForTest(user(first), 1, 5);
        game.setChamberForTest(user(first), true, false, true);
        game.useItem(user(first), 0);
        MultiPackets.ItemUsed mine = await(user(first), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == 2);
        MultiPackets.ItemUsed theirs = await(user(other), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == 2);
        assertEquals(1, mine.shell());
        assertEquals(-1, theirs.shell());
        assertEquals(3, mine.shellsLeft());
        sleep(150);
        game.useItem(user(first), 1);
        MultiPackets.ItemUsed beer = await(user(other), PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, x -> x.itemId() == 5);
        assertEquals(1, beer.shell());
        assertEquals(2, beer.shellsLeft());
    }

    @Test
    @DisplayName("담배: 라운드 시작 체력보다 낮을 때만 1 회복")
    void cigarettesHeal() {
        int first = startTurns();
        game.setItemForTest(user(first), 0, 4);
        game.useItem(user(first), 0);
        MultiPackets.ItemUsed used = nth(PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, 0);
        assertEquals(MultiGameService.STARTING_HEALTH, used.health()[first]);
    }

    @Test
    @DisplayName("맥주로 마지막 셸을 빼면 다음 사람부터 아이템을 다시 받는다")
    void beerLastShellRegrabs() {
        int first = startTurns();
        game.setItemForTest(user(first), 7, 5);
        game.setChamberForTest(user(first), false);
        game.useItem(user(first), 7);
        assertEquals(0, nth(PacketType.MULTI_ITEM_USED, MultiPackets.ItemUsed.class, 0).shellsLeft());
        nth(PacketType.MULTI_ITEM_PHASE_BEGIN, ItemPhaseBegin.class, 1);
    }

    @Test
    @DisplayName("셸이 다 떨어지면 빈 칸이 있는 자리가 아이템을 다시 받고 다시 장전한다 (다음 차례는 쏜 사람 다음)")
    void shellsEmptyRegrabsItems() {
        int first = startTurns();
        game.setChamberForTest(user(first), true);
        game.pickupShotgun(user(first));
        game.shoot(user(first), (first + 1) % 3);
        Shot shot = nth(PacketType.MULTI_SHOT, Shot.class, 0);
        assertEquals(0, shot.shellsLeft());
        ItemPhaseBegin begin = nth(PacketType.MULTI_ITEM_PHASE_BEGIN, ItemPhaseBegin.class, 1);
        // 칸 8개 중 4개가 차 있어서 4개씩 다시 받는다
        for (int n : begin.itemCounts()) assertEquals(MultiGameService.FIXED_ITEM_COUNT, n);
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, MultiGameService.FIXED_ITEM_COUNT + i);
            }
        }
        LoadShotgun load = nth(PacketType.MULTI_LOAD_SHOTGUN, LoadShotgun.class, 1);
        assertEquals((first + 1) % 3, load.firstTurnSeat());
    }

    @Test
    @DisplayName("한 명만 남으면 라운드가 끝나고, 3라운드가 지나면 게임이 끝난다")
    void roundsEndAfterLastSurvivor() {
        int shooter = startTurns();
        int[] damage = new int[3], deaths = new int[3], cash = new int[3];
        RoundEnd end = null;
        for (int round = 0; round < MultiGameService.ROUNDS; round++) {
            if (round > 0) shooter = firstTurnOfRound(round);
            boolean[] alive = {true, true, true};
            while (aliveCount(alive) > 1) {
                // 셸은 모두 실탄으로 바꿔 둔다
                game.setChamberForTest(user(shooter), true, true, true, true, true, true, true, true);
                int target = otherAlive(alive, shooter);
                game.pickupShotgun(user(shooter));
                game.shoot(user(shooter), target);
                Shot shot = nth(PacketType.MULTI_SHOT, Shot.class, shotsSeen++);
                assertEquals(shooter, shot.shooter());
                damage[shooter]++;
                deaths[target]++;
                alive[target] = shot.health()[target] > 0;
                if (aliveCount(alive) > 1) shooter = nth(PacketType.MULTI_TURN, Turn.class, turnsSeen++).seat();
            }
            int winner = alive[0] ? 0 : alive[1] ? 1 : 2;
            cash[winner] += MultiGameService.ROUND_CASH[round];
            end = nth(PacketType.MULTI_ROUND_END, RoundEnd.class, round);
            assertEquals(round, end.round());
            assertEquals(winner, end.winnerSeat());
            assertEquals(round == MultiGameService.ROUNDS - 1, end.finalRound());
            assertEquals(round == MultiGameService.ROUNDS - 1, end.results() != null, "결과는 마지막 라운드에만 온다");
        }
        // 원작 GetMatchResultStatistics: 가장 큰 값(같으면 뒤 자리), 모두 0이면 -1, 아이템은 가장 적은 사람(같으면 뒤 자리)
        MultiPackets.MatchResults r = end.results();
        assertEquals(lastMax(damage), r.mostDamage());
        assertEquals(lastMax(deaths), r.mostDeaths());
        assertEquals(lastMax(cash), r.wealthiest());
        assertEquals(-1, r.chimney());
        assertEquals(-1, r.leastCareful());
        assertEquals(2, r.mostResourceful());
        sleep(600);
        int picked = count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class);
        for (long u = 1; u <= 3; u++) game.pickupShotgun(u);   // 끝난 게임은 더 이상 받지 않는다
        sleep(100);
        assertEquals(picked, count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class));
    }

    private int shotsSeen;
    private int turnsSeen;

    private static int lastMax(int[] values) {
        int found = -1, best = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= best) {
                best = values[i];
                found = i;
            }
        }
        return best == 0 ? -1 : found;
    }

    /** userId 1이 받은 type 패킷의 n번째 (0부터). 아직 안 왔으면 4초까지 기다린다 */
    private <T> T nth(String type, Class<T> cls, int n) {
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) {
            synchronized (sent) {
                List<T> all = sent.stream().filter(s -> s.userId == 1 && s.type.equals(type) && cls.isInstance(s.data)).map(s -> cls.cast(s.data)).toList();
                if (all.size() > n) return all.get(n);
            }
            sleep(10);
        }
        throw new AssertionError(type + " #" + n + "이 안 왔다");
    }

    /** 다음 라운드 시작 -> 아이템 받기 -> 장전 -> 첫 차례까지 가서 그 자리를 돌려준다 */
    private int firstTurnOfRound(int round) {
        RoundStart start = nth(PacketType.MULTI_ROUND_START, RoundStart.class, round);
        assertEquals(round, start.round());
        assertEquals(MultiGameService.STARTING_HEALTH, start.health());
        nth(PacketType.MULTI_ITEM_PHASE_BEGIN, ItemPhaseBegin.class, round);
        for (long u = 1; u <= 3; u++) {
            for (int i = 0; i < MultiGameService.FIXED_ITEM_COUNT; i++) {
                game.grabItem(u);
                game.placeItem(u, i);
            }
        }
        LoadShotgun load = nth(PacketType.MULTI_LOAD_SHOTGUN, LoadShotgun.class, round);
        sleep(200);
        return load.firstTurnSeat();
    }

    private static int aliveCount(boolean[] alive) {
        int n = 0;
        for (boolean a : alive) if (a) n++;
        return n;
    }

    private static int otherAlive(boolean[] alive, int from) {
        for (int k = 1; k < alive.length; k++) {
            int s = (from + k) % alive.length;
            if (alive[s]) return s;
        }
        return from;
    }
}
