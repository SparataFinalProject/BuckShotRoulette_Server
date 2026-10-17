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
    private com.buckshot.multi.MultiGameService multi;
    private DevRoomService rooms;

    @BeforeEach
    void setUp() {
        sender = mock(PacketSender.class);
        match = mock(DevMatchService.class);
        multi = mock(com.buckshot.multi.MultiGameService.class);
        UserService users = mock(UserService.class);
        when(users.getProfile(anyLong())).thenAnswer(i -> new PlayerProfileResponse(i.getArgument(0), "p" + i.getArgument(0), 0, 0, 1000, "BRONZE"));
        rooms = new DevRoomService(sender, users, match, multi);
    }

    /** 3~4인 방을 만들어 시작하고, 게임이 끝났을 때 부를 콜백을 돌려준다 */
    private Runnable startFourPlayer(int total) {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        for (long u = 2; u <= total; u++) rooms.join(u, null, code(1));
        rooms.start(1);
        ArgumentCaptor<Runnable> ended = ArgumentCaptor.forClass(Runnable.class);
        verify(multi).create(any(), org.mockito.ArgumentMatchers.anyInt(), ended.capture());
        return ended.getValue();
    }

    private int stateCount(long userId) {
        return (int) org.mockito.Mockito.mockingDetails(sender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendTo") && i.getArgument(0).equals(userId) && PacketType.ROOM_STATE.equals(i.getArgument(1)))
                .count();
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
    @DisplayName("3~4인 방은 게임 동안 남아 있다가(목록에 안 보이고 못 들어옴) 게임이 끝나면 같은 사람끼리 다시 열린다")
    void fourPlayerRoomReopensAfterGame() {
        Runnable ended = startFourPlayer(3);
        String code = code(1);
        // 게임 중: 목록에 없고, 코드로도 못 들어오고, 상태를 물어도 답하지 않는다
        rooms.watch(7, true);
        ArgumentCaptor<Object> list = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendTo(eq(7L), eq(PacketType.ROOM_LIST), list.capture());
        assertTrue(((DevRoomPackets.RoomList) list.getValue()).rooms().isEmpty());
        rooms.join(9, null, code);
        verify(sender).sendTo(9L, PacketType.ROOM_JOIN_FAILED, new DevRoomPackets.JoinFailed(DevRoomPackets.JoinFailReason.CLOSED));
        int before = stateCount(2);
        rooms.requestState(2);
        assertEquals(before, stateCount(2));

        // 게임이 끝나면 모두에게 같은 방(같은 코드, 같은 방장, 참가 순서 그대로)
        ended.run();
        for (long u = 1; u <= 3; u++) {
            var s = lastState(u);
            assertEquals(code, s.code());
            assertEquals(1L, s.hostUserId());
            assertEquals(java.util.List.of(1L, 2L, 3L), s.members().stream().map(DevRoomPackets.Member::userId).toList());
        }
        // 로비로 돌아와 물으면 다시 알려 준다
        before = stateCount(3);
        rooms.requestState(3);
        assertEquals(before + 1, stateCount(3));
    }

    @Test
    @DisplayName("게임 중에 끊긴 사람은 다시 열린 방에 없다")
    void leaverIsNotInReopenedRoom() {
        Runnable ended = startFourPlayer(4);
        rooms.onDisconnected(new com.buckshot.ws.session.UserDisconnectedEvent(3));
        ended.run();
        assertEquals(java.util.List.of(1L, 2L, 4L), lastState(1).members().stream().map(DevRoomPackets.Member::userId).toList());
        // 끊긴 사람은 방이 없다
        int before = stateCount(3);
        rooms.requestState(3);
        assertEquals(before, stateCount(3));
    }

    @Test
    @DisplayName("라운드 수는 기본 3이고, 방장이 정하면 상태에 반영되어 게임 시작에 그대로 쓴다")
    void hostSetsRounds() {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        assertEquals(3, lastState(1).rounds());

        rooms.setRounds(1, 1);
        assertEquals(1, lastState(1).rounds());

        for (long u = 2; u <= 3; u++) rooms.join(u, null, code(1));
        rooms.start(1);
        ArgumentCaptor<Integer> rounds = ArgumentCaptor.forClass(Integer.class);
        verify(multi).create(any(), rounds.capture(), any());
        assertEquals(1, rounds.getValue());
    }

    @Test
    @DisplayName("방장이 아니면 라운드 수를 못 바꾸고, 1~3 밖의 값은 맞춰진다")
    void roundsAreHostOnlyAndClamped() {
        rooms.create(1);
        rooms.setFourPlayer(1, true);
        rooms.join(2, null, code(1));

        rooms.setRounds(2, 1);   // 방장이 아님
        assertEquals(3, lastState(1).rounds());

        rooms.setRounds(1, 0);
        assertEquals(1, lastState(1).rounds());
        rooms.setRounds(1, 99);
        assertEquals(3, lastState(1).rounds());
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
