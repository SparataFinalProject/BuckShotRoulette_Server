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
        game.timing = new MultiGameService.Timing(0.05, 0.02, 1, 1, 0.1, 0.05, 0.3, 0.1);
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
                alive[target] = shot.health()[target] > 0;
                if (aliveCount(alive) > 1) shooter = nth(PacketType.MULTI_TURN, Turn.class, turnsSeen++).seat();
            }
            int winner = alive[0] ? 0 : alive[1] ? 1 : 2;
            RoundEnd end = nth(PacketType.MULTI_ROUND_END, RoundEnd.class, round);
            assertEquals(round, end.round());
            assertEquals(winner, end.winnerSeat());
            assertEquals(round == MultiGameService.ROUNDS - 1, end.finalRound());
        }
        sleep(600);
        int picked = count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class);
        for (long u = 1; u <= 3; u++) game.pickupShotgun(u);   // 끝난 게임은 더 이상 받지 않는다
        sleep(100);
        assertEquals(picked, count(1, PacketType.MULTI_SHOTGUN_PICKED, ShotgunPicked.class));
    }

    private int shotsSeen;
    private int turnsSeen;

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
