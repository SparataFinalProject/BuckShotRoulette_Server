package com.buckshot.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.buckshot.devtest.DevRoomPackets.Member;
import com.buckshot.multi.MultiPackets.LoadShotgun;
import com.buckshot.multi.MultiPackets.PlayerLeft;
import com.buckshot.multi.MultiPackets.RoundEnd;
import com.buckshot.multi.MultiPackets.ShotgunDropped;
import com.buckshot.multi.MultiPackets.Turn;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.session.UserDisconnectedEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 게임 도중 연결이 끊긴 사람 (원작 RemoveInstancesFromGame): 남은 사람들에게 알리고, 차례를 넘기고, 한 명만 남으면 라운드를 끝낸다 */
class MultiLeaveTest {

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

    private <T> T await(long userId, String type, Class<T> cls, Predicate<T> match) {
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) {
            synchronized (sent) {
                for (Sent s : sent) {
                    if (s.userId == userId && s.type.equals(type) && cls.isInstance(s.data) && match.test(cls.cast(s.data))) return cls.cast(s.data);
                }
            }
            sleep(10);
        }
        return null;
    }

    private <T> int count(long userId, String type, Class<T> cls) {
        synchronized (sent) {
            return (int) sent.stream().filter(s -> s.userId == userId && s.type.equals(type) && cls.isInstance(s.data)).count();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long user(int seat) {
        return seat + 1L;   // 자리 번호 = 참가 순서
    }

    private void leave(int seat) {
        game.onDisconnected(new UserDisconnectedEvent(user(seat)));
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
        sleep(200);
        return load.firstTurnSeat();
    }

    @Test
    @DisplayName("나간 사람은 남은 사람들에게 알리고, 그 뒤 차례는 나간 자리를 건너뛴다")
    void leaveIsBroadcastAndSkipped() {
        int first = startTurns();
        int left = (first + 1) % 3;
        int other = (first + 2) % 3;
        leave(left);
        for (int seat : new int[] { first, other }) {
            PlayerLeft p = await(user(seat), PacketType.MULTI_PLAYER_LEFT, PlayerLeft.class, x -> true);
            assertNotNull(p, "나갔다는 알림이 안 왔다");
            assertEquals(left, p.seat());
        }
        // 첫 차례가 시간이 다 되면 나간 자리를 건너뛰고 남은 사람에게 넘어간다
        Turn turn = await(user(first), PacketType.MULTI_TURN, Turn.class, x -> true);
        assertNotNull(turn);
        assertEquals(other, turn.seat());
        assertEquals(0, count(user(first), PacketType.MULTI_ROUND_END, RoundEnd.class));
    }

    @Test
    @DisplayName("차례인 사람이 나가면 바로 다음 사람에게 넘어간다")
    void currentTurnLeaves() {
        int first = startTurns();
        leave(first);
        Turn turn = await(user((first + 1) % 3), PacketType.MULTI_TURN, Turn.class, x -> true);
        assertNotNull(turn);
        assertEquals((first + 1) % 3, turn.seat());
        assertEquals("timeout", turn.reason());
    }

    @Test
    @DisplayName("총을 들고 대상을 고르던 사람이 나가면 총을 내려놓고 다음 사람에게 넘어간다")
    void leaveWhileHoldingShotgun() {
        int first = startTurns();
        game.pickupShotgun(user(first));
        leave(first);
        int next = (first + 1) % 3;
        ShotgunDropped dropped = await(user(next), PacketType.MULTI_SHOTGUN_DROPPED, ShotgunDropped.class, x -> true);
        assertNotNull(dropped);
        assertEquals(first, dropped.seat());
        Turn turn = await(user(next), PacketType.MULTI_TURN, Turn.class, x -> true);
        assertNotNull(turn);
        assertEquals(next, turn.seat());
    }

    @Test
    @DisplayName("한 명만 남으면 그 사람이 이긴 것으로 라운드가 끝난다")
    void lastOneWins() {
        int first = startTurns();
        int a = (first + 1) % 3;
        int b = (first + 2) % 3;
        leave(a);
        leave(b);
        RoundEnd end = await(user(first), PacketType.MULTI_ROUND_END, RoundEnd.class, x -> true);
        assertNotNull(end, "라운드 종료가 안 왔다");
        assertEquals(first, end.winnerSeat());
        assertTrue(count(user(first), PacketType.MULTI_PLAYER_LEFT, PlayerLeft.class) == 2);
        // 라운드가 남아 있어도 혼자서는 다음 라운드를 하지 않고 게임이 끝난다
        assertTrue(end.finalRound(), "혼자 남았는데 마지막 라운드가 아니다");
        assertNotNull(end.results(), "결과가 없다");
    }

    @Test
    @DisplayName("아이템 상자가 나오기 전에 나머지가 모두 나가면 기다리지 않고 바로 게임이 끝난다")
    void lastOneWinsBeforeItems() {
        for (long u = 1; u <= 3; u++) game.ready(u);
        leave(1);
        leave(2);
        RoundEnd end = await(user(0), PacketType.MULTI_ROUND_END, RoundEnd.class, x -> true);
        assertNotNull(end, "라운드 종료가 안 왔다");
        assertEquals(0, end.winnerSeat());
        assertTrue(end.finalRound(), "혼자 남았는데 마지막 라운드가 아니다");
        // 아이템 받기·장전·다음 라운드로 가지 않는다
        sleep(1500);
        assertEquals(0, count(user(0), PacketType.MULTI_LOAD_SHOTGUN, LoadShotgun.class));
        assertEquals(1, count(user(0), PacketType.MULTI_ROUND_END, RoundEnd.class));
        assertEquals(1, count(user(0), PacketType.MULTI_ROUND_START, MultiPackets.RoundStart.class));
    }
}
