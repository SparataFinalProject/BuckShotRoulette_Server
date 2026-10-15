package com.buckshot.matchmaking.scheduler;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.s2c.MatchCancelledPacket;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchmakingTimeoutScheduler {

    private static final String TIMEOUT = "TIMEOUT";

    private final MatchmakingService matchmakingService;
    private final PacketSender packetSender;

    @Scheduled(fixedDelay = 1000)
    public void cancelTimedOutUsers() {
        List<Long> timedOutUsers =
                matchmakingService.removeTimedOutUsers(
                        System.currentTimeMillis()
                );

        for (long userId : timedOutUsers) {
            log.info(
                    "match cancelled userId={} reason={}",
                    userId,
                    TIMEOUT
            );

            packetSender.sendTo(
                    userId,
                    PacketType.MATCH_CANCELLED,
                    new MatchCancelledPacket(TIMEOUT)
            );
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void cancelReadyTimedOutMatches() {
        List<MatchmakingService.ReadyTimeoutMatch> timedOutMatches =
                matchmakingService.removeReadyTimedOutMatches(
                        System.currentTimeMillis()
                );

        for (MatchmakingService.ReadyTimeoutMatch match : timedOutMatches) {
            log.info(
                    "match cancelled gameId={} playerA={} playerB={} reason=READY_TIMEOUT",
                    match.gameId(),
                    match.playerAId(),
                    match.playerBId()
            );

            MatchCancelledPacket packet =
                    new MatchCancelledPacket("READY_TIMEOUT");

            // 양쪽 플레이어에게 준비 시간 초과 알림
            packetSender.sendTo(
                    match.playerAId(),
                    PacketType.MATCH_CANCELLED,
                    packet
            );

            packetSender.sendTo(
                    match.playerBId(),
                    PacketType.MATCH_CANCELLED,
                    packet
            );
        }
    }
}