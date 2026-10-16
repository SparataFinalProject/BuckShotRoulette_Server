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

/** 짝이 정해진 두 사람에게 서로의 프로필을 담아 MATCH_FOUND를 보낸다 (바로 매칭과 랭크 매칭 공통). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundSender {

    private final PacketSender packetSender;
    private final UserService userService;

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
