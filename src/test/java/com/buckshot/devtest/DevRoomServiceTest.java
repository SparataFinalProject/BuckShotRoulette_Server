package com.buckshot.devtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevRoomServiceTest {

    private PacketSender sender;
    private DevMatchService match;
    private DevRoomService rooms;

    @BeforeEach
    void setUp() {
        sender = mock(PacketSender.class);
        match = mock(DevMatchService.class);
        UserService users = mock(UserService.class);
        when(users.getProfile(anyLong())).thenAnswer(i -> new PlayerProfileResponse(i.getArgument(0), "p" + i.getArgument(0), 0, 0, 1000, "BRONZE"));
        rooms = new DevRoomService(sender, users, match);
    }

    private String code(long host) {
        ArgumentCaptor<Object> state = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendTo(eq(host), eq(PacketType.ROOM_STATE), state.capture());
        return ((DevRoomPackets.State) state.getValue()).code();
    }

    private DevRoomPackets.State lastState(long userId) {
        ArgumentCaptor<Object> state = ArgumentCaptor.forClass(Object.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendTo(eq(userId), eq(PacketType.ROOM_STATE), state.capture());
        return (DevRoomPackets.State) state.getValue();
    }

    private void fill(long host, int total) {
        rooms.create(host);
        String code = code(host);
        for (long u = host + 1; u < host + total; u++) rooms.join(u, null, code);
    }

    @Test
    @DisplayName("기본 방은 두 명이 가득이고 세 번째는 FULL")
    void defaultRoomHoldsTwo() {
        fill(1, 2);
        rooms.join(9, null, code(1));
        verify(sender).sendTo(9L, PacketType.ROOM_JOIN_FAILED, new DevRoomPackets.JoinFailed(DevRoomPackets.JoinFailReason.FULL));
        assertEquals(2, lastState(1).maxMembers());
    }

    @Test
    @DisplayName("3~4인 전용을 켜면 정원이 4명, 다섯 번째는 FULL")
    void fourPlayerHoldsFour() {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        assertEquals(4, lastState(1).maxMembers());
        String code = code(1);
        for (long u = 2; u <= 4; u++) rooms.join(u, null, code);
        assertEquals(4, lastState(1).members().size());
        rooms.join(9, null, code);
        verify(sender).sendTo(9L, PacketType.ROOM_JOIN_FAILED, new DevRoomPackets.JoinFailed(DevRoomPackets.JoinFailReason.FULL));
    }

    @Test
    @DisplayName("방장만 켤 수 있고, 3명 이상이면 끌 수 없다")
    void cannotTurnOffWithThreeOrMore() {
        rooms.create(1);
        rooms.join(2, null, code(1));
        rooms.setFourPlayer(2, true);   // 방장이 아님
        assertEquals(2, lastState(1).maxMembers());

        rooms.setFourPlayer(1, true);
        rooms.join(3, null, code(1));
        rooms.setFourPlayer(1, false);   // 3명이라 거절
        assertEquals(4, lastState(1).maxMembers());

        rooms.kick(1, 3);
        rooms.setFourPlayer(1, false);   // 2명으로 줄었으니 끌 수 있다
        assertEquals(2, lastState(1).maxMembers());
    }

    @Test
    @DisplayName("3~4인 전용 방은 3명 이상일 때만 시작하고 ROOM_START_MULTI를 모두에게 보낸다")
    void fourPlayerStart() {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        rooms.join(2, null, code(1));
        rooms.start(1);   // 2명이라 시작 안 됨
        verify(sender, never()).sendTo(anyLong(), eq(PacketType.ROOM_START_MULTI), any());

        rooms.join(3, null, code(1));
        rooms.start(1);
        for (long u = 1; u <= 3; u++) verify(sender).sendTo(eq(u), eq(PacketType.ROOM_START_MULTI), any());
        verify(match, never()).startPair(anyLong(), anyLong());
    }

    @Test
    @DisplayName("기본 방은 두 명이면 기존처럼 1대1 매칭으로 시작")
    void defaultStartIsPair() {
        fill(1, 2);
        rooms.start(1);
        verify(match).startPair(1L, 2L);
        verify(sender, never()).sendTo(anyLong(), eq(PacketType.ROOM_START_MULTI), any());
    }

    @Test
    @DisplayName("방 목록의 정원도 방마다 다르다")
    void listShowsPerRoomMax() {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        rooms.watch(7, true);
        ArgumentCaptor<Object> list = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(7L), eq(PacketType.ROOM_LIST), list.capture());
        var summaries = ((DevRoomPackets.RoomList) list.getValue()).rooms();
        assertEquals(1, summaries.size());
        assertEquals(4, summaries.get(0).maxMembers());
        assertTrue(summaries.get(0).members() <= 4);
        assertFalse(summaries.isEmpty());
    }
}
