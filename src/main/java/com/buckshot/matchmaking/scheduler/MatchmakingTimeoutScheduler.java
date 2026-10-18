package com.buckshot.matchmaking.scheduler;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.matchmaking.ws.MatchFoundSender;
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
    private final MatchFoundSender matchFoundSender;

    // 랭크 매칭은 기다릴수록 허용 레이팅 차이가 넓어지므로 새로 들어오는 사람이 없어도 1초마다 짝을 찾는다
    @Scheduled(fixedDelay = 1000)
    public void matchRankedUsers() {
        // MATCH_JOIN 처리(대기열 등록 + MATCH_QUEUED 전송)와 같은 잠금을 써서
        // 방금 들어온 사람에게 MATCH_FOUND가 MATCH_QUEUED보다 먼저 가지 않게 한다
        matchFoundSender.runInRankedOrder(() ->
                matchmakingService
                        .matchRanked(System.currentTimeMillis())
                        .forEach(matchFoundSender::send));
    }

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