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

        boolean ranked = data != null && data.ranked();

        MatchmakingService.JoinResult result;

        if (ranked) {
            long now = System.currentTimeMillis();
            int rating = userService.getProfile(userId).rating();

            result = matchmakingService.joinRanked(userId, rating, now);
        } else {
            result = matchmakingService.join(userId);
        }

        // MATCH_JOIN을 보낸 사용자는 우선
        // MATCH_QUEUED를 받는다.
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

        if (ranked) {
            // 방금 들어온 사람과 짝이 될 상대가 이미 기다리고 있을 수 있다.
            // 그 뒤로는 MatchmakingTimeoutScheduler가 주기적으로 짝을 찾는다.
            matchmakingService
                    .matchRanked(System.currentTimeMillis())
                    .forEach(matchFoundSender::send);
            return;
        }

        if (!result.matched()) {
            return;
        }

        matchFoundSender.send(result.match());
    }
}
