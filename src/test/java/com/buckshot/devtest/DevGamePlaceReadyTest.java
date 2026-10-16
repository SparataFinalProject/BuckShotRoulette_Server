package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.Target;
import com.buckshot.ws.packet.s2c.ItemPlacedPacket;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 아이템 배치 제한 시간은 클라가 상자를 열었다고 알린(PLACE_READY) 사람마다 따로 센다. */
class DevGamePlaceReadyTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private final PacketSender sender = mock(PacketSender.class);
    private final DevGameRoom room = new DevGameRoom(sender, A, B, DevGameRoom.Listener.NONE, new Random(7));

    // 첫 장전(아이템 없음)의 탄을 모두 자기에게 쏴서 다 쓰고, 첫 아이템 배치에 들어간다 (공포탄이면 차례가 이어지고 실탄은 1발뿐)
    private void reachPlacing() {
        room.begin();
        for (int i = 0; i < 20 && room.turnUserId() != 0; i++) room.fire(room.turnUserId(), Target.SELF);
        assertEquals(0, room.turnUserId(), "아이템 배치에 들어가지 않았다");
    }

    private long autoPlacedFor(long userId) {
        return mockingDetails(sender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("broadcast") || i.getMethod().getName().equals("sendTo"))
                .flatMap(i -> Arrays.stream(i.getArguments()))
                .filter(o -> o instanceof ItemPlacedPacket p && p.auto() && p.userId() == userId)
                .count();
    }

    @Test
    @DisplayName("PLACE_READY만으로는 아무것도 대신 놓지 않고, 그 사람의 제한 시간이 지나면 그 사람 것만 놓는다")
    void timeoutPerUser() {
        reachPlacing();
        room.placeReady(A);
        assertEquals(0, autoPlacedFor(A));

        room.placeTimeoutFor(A, room.placingId());
        assertTrue(Arrays.stream(room.itemsOf(A)).anyMatch(Objects::nonNull), "A의 아이템이 놓이지 않았다");
        assertTrue(Arrays.stream(room.itemsOf(B)).allMatch(Objects::isNull), "B는 아직 상자를 안 열었는데 놓였다");
        assertEquals(0, room.turnUserId(), "B가 아직 배치 중인데 장전으로 넘어갔다");

        room.placeReady(B);
        room.placeTimeoutFor(B, room.placingId());
        assertNotEquals(0, room.turnUserId(), "둘 다 놓였는데 장전으로 넘어가지 않았다");
    }

    @Test
    @DisplayName("지난 배치의 타이머는 무시한다")
    void staleTimerIgnored() {
        reachPlacing();
        int old = room.placingId() - 1;
        room.placeTimeoutFor(A, old);
        assertTrue(Arrays.stream(room.itemsOf(A)).allMatch(Objects::isNull));
        assertEquals(0, room.turnUserId());
    }
}
