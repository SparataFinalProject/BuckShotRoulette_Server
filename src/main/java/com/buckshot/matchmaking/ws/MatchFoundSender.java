package com.buckshot.matchmaking.ws;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.dto.PlayerProfile;
import com.buckshot.ws.packet.s2c.MatchFoundPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/** 짝이 정해진 두 사람에게 서로의 프로필을 담아 MATCH_FOUND를 보낸다 (바로 매칭과 랭크 매칭 공통). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundSender {

    private final PacketSender packetSender;
    private final UserService userService;

    // 랭크 매칭: 대기열 등록 + MATCH_QUEUED 전송과 (스케줄러의) 짝 찾기가 서로 끼어들지 않게 하는 잠금
    private final Object rankedOrderLock = new Object();

    /**
     * action(대기열 등록·MATCH_QUEUED 전송·짝 찾기)을 랭크 매칭 순서 잠금 안에서 실행하고, 짝 결과만 돌려준다.
     * 새로 들어온 사람이 대기열에 등록된 직후 스케줄러가 짝을 찾아 MATCH_FOUND를 먼저 보내면
     * 클라이언트는 MATCH_QUEUED보다 MATCH_FOUND를 먼저 받게 된다. 둘이 같은 잠금을 쓰면 그럴 수 없다.
     * 짝이 된 사람은 이미 MATCH_QUEUED를 받은 뒤이므로, MATCH_FOUND 전송(프로필 DB 조회 포함)은
     * 호출한 쪽이 잠금 밖에서 한다. 잠금 안에서 DB를 조회하면 동시 요청이 줄을 서서 매칭이 느려진다.
     */
    public List<MatchmakingService.MatchResult> pairInRankedOrder(
            Supplier<List<MatchmakingService.MatchResult>> action) {
        synchronized (rankedOrderLock) {
            return action.get();
        }
    }

    public void send(MatchmakingService.MatchResult match) {
        PlayerProfile playerA =
                getPlayerProfile(match.playerAId());

        PlayerProfile playerB =
                getPlayerProfile(match.playerBId());

        packetSender.sendTo(
                match.playerAId(),
                PacketType.MATCH_FOUND,
                new MatchFoundPacket(
                        match.gameId(),
                        playerB,
                        match.readyTimeoutSec(),
                        -1 // 임시
                )
        );

        packetSender.sendTo(
                match.playerBId(),
                PacketType.MATCH_FOUND,
                new MatchFoundPacket(
                        match.gameId(),
                        playerA,
                        match.readyTimeoutSec(),
                        -1 // 임시
                )
        );

        log.info(
                "match found gameId={} playerA={} playerB={} ranked={}",
                match.gameId(),
                match.playerAId(),
                match.playerBId(),
                match.ranked()
        );
    }

    private PlayerProfile getPlayerProfile(long userId) {
        PlayerProfileResponse profile =
                userService.getProfile(userId);

        return new PlayerProfile(
                profile.userId(),
                profile.nickname(),
                profile.wins(),
                profile.losses(),
                profile.rating(),
                profile.tier()
        );
    }
}
