package com.buckshot.matchmaking.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.buckshot.matchmaking.scheduler.MatchmakingTimeoutScheduler;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.MatchJoinPacket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 랭크 매칭에서 MATCH_QUEUED가 항상 MATCH_FOUND보다 먼저 나가는지.
 * 랭크 스케줄러(다른 스레드)가 "대기열 등록과 MATCH_QUEUED 전송 사이"에 끼어드는 상황을 만들어 확인한다.
 */
class RankedQueueOrderTest {

    @Test
    @DisplayName("새로 들어온 사람에게는 스케줄러가 끼어들어도 MATCH_QUEUED가 MATCH_FOUND보다 먼저 간다")
    void queuedAlwaysBeforeFoundEvenWhenSchedulerTicksInBetween() throws Exception {
        List<String> sent = new CopyOnWriteArrayList<>();
        PacketSender sender = mock(PacketSender.class);
        doAnswer(i -> {
            sent.add(i.getArgument(1) + "->" + i.getArgument(0));
            return null;
        }).when(sender).sendTo(anyLong(), anyString(), any());

        UserService users = mock(UserService.class);
        when(users.getProfile(anyLong())).thenAnswer(i ->
                new PlayerProfileResponse(i.getArgument(0), "p" + i.getArgument(0), 0, 0, 1000, "SILVER"));

        MatchmakingService real = new MatchmakingService();
        MatchFoundSender found = new MatchFoundSender(sender, users);
        MatchmakingTimeoutScheduler scheduler = new MatchmakingTimeoutScheduler(real, sender, found);

        // 1번이 먼저 랭크 대기 중
        new MatchJoinHandler(real, sender, users, found).handle(new WsContext(1L), new MatchJoinPacket("RANKED"));
        sent.clear();

        // 2번이 들어온다. joinRanked가 끝난 직후 다른 스레드에서 스케줄러 틱이 실행된다.
        // 틱이 끝날 때까지 최대 0.5초 기다린다: 순서 보장이 없으면 틱이 바로 끝나 FOUND가 먼저 나가고,
        // 보장이 있으면 틱은 2번의 처리가 끝날 때까지 막혀 있다가 그 뒤에 실행된다.
        CountDownLatch tickDone = new CountDownLatch(1);
        MatchmakingService racing = spy(real);
        doAnswer(i -> {
            Object result = i.callRealMethod();
            Thread tick = new Thread(() -> {
                scheduler.matchRankedUsers();
                tickDone.countDown();
            }, "scheduler-tick");
            tick.start();
            tickDone.await(500, TimeUnit.MILLISECONDS);
            return result;
        }).when(racing).joinRanked(eq(2L), anyInt(), anyLong());

        new MatchJoinHandler(racing, sender, users, found).handle(new WsContext(2L), new MatchJoinPacket("RANKED"));
        assertTrue(tickDone.await(5, TimeUnit.SECONDS), "스케줄러 틱이 끝나지 않았다 (교착?)");

        int queued = sent.indexOf(PacketType.MATCH_QUEUED + "->2");
        int foundTo2 = sent.indexOf(PacketType.MATCH_FOUND + "->2");
        assertTrue(queued >= 0 && foundTo2 >= 0, "둘 다 보내져야 한다: " + sent);
        assertTrue(queued < foundTo2, "MATCH_QUEUED가 MATCH_FOUND보다 먼저여야 한다: " + sent);
        assertEquals(1, sent.stream().filter(s -> s.equals(PacketType.MATCH_FOUND + "->1")).count());
    }
}
