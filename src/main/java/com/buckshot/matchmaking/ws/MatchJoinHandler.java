package com.buckshot.matchmaking.ws;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.MatchJoinPacket;
import com.buckshot.ws.packet.s2c.MatchQueuedPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchJoinHandler
        implements PacketHandler<MatchJoinPacket> {

    private final MatchmakingService matchmakingService;
    private final PacketSender packetSender;
    private final UserService userService;
    private final MatchFoundSender matchFoundSender;

    @Override
    public String type() {
        return PacketType.MATCH_JOIN;
    }

    @Override
    public Class<MatchJoinPacket> dataType() {
        return MatchJoinPacket.class;
    }

    @Override
    public void handle(
            WsContext ctx,
            MatchJoinPacket data
    ) {
        long userId = ctx.userId();

        if (data != null && data.ranked()) {
            handleRanked(userId);
            return;
        }

        MatchmakingService.JoinResult result =
                matchmakingService.join(userId);

        sendQueued(userId, result, false);

        if (!result.matched()) {
            return;
        }

        matchFoundSender.send(result.match());
    }

    private void handleRanked(long userId) {
        // DB 조회는 잠금 밖에서
        int rating = userService.getProfile(userId).rating();

        // 대기열 등록 -> MATCH_QUEUED 전송 -> 짝 찾기·MATCH_FOUND 전송을 한 덩어리로 실행한다.
        // 스케줄러(MatchmakingTimeoutScheduler)의 짝 찾기도 같은 잠금을 써서,
        // 등록 직후 스케줄러가 먼저 짝을 지어 MATCH_FOUND가 MATCH_QUEUED보다 앞서 가는 일이 없다.
        matchFoundSender.runInRankedOrder(() -> {
            MatchmakingService.JoinResult result =
                    matchmakingService.joinRanked(
                            userId,
                            rating,
                            System.currentTimeMillis()
                    );

            sendQueued(userId, result, true);

            // 방금 들어온 사람과 짝이 될 상대가 이미 기다리고 있을 수 있다.
            // 그 뒤로는 MatchmakingTimeoutScheduler가 주기적으로 짝을 찾는다.
            matchmakingService
                    .matchRanked(System.currentTimeMillis())
                    .forEach(matchFoundSender::send);
        });
    }

    // MATCH_JOIN을 보낸 사용자는 우선 MATCH_QUEUED를 받는다.
    private void sendQueued(
            long userId,
            MatchmakingService.JoinResult result,
            boolean ranked
    ) {
        packetSender.sendTo(
                userId,
                PacketType.MATCH_QUEUED,
                new MatchQueuedPacket(
                        result.queuedAt(),
                        result.timeoutSec(),
                        -1 // 임시: 상대 스킨 미지정
                )
        );

        log.info(
                "match queued userId={} queuedAt={} ranked={}",
                userId,
                result.queuedAt(),
                ranked
        );
    }
}
