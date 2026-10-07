package com.buckshot.matchmaking.ws;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.user.dto.PlayerProfileResponse;
import com.buckshot.user.service.UserService;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.MatchJoinPacket;
import com.buckshot.ws.packet.dto.PlayerProfile;
import com.buckshot.ws.packet.s2c.MatchFoundPacket;
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

        MatchmakingService.JoinResult result =
                matchmakingService.join(userId);

        // MATCH_JOIN을 보낸 사용자는 우선
        // MATCH_QUEUED를 받는다.
        packetSender.sendTo(
                userId,
                PacketType.MATCH_QUEUED,
                new MatchQueuedPacket(
                        result.queuedAt(),
                        result.timeoutSec()
                )
        );

        log.info(
                "match queued userId={} queuedAt={}",
                userId,
                result.queuedAt()
        );

        if (!result.matched()) {
            return;
        }

        sendMatchFound(result.match());
    }

    private void sendMatchFound(
            MatchmakingService.MatchResult match
    ) {
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
                        match.readyTimeoutSec()
                )
        );

        packetSender.sendTo(
                match.playerBId(),
                PacketType.MATCH_FOUND,
                new MatchFoundPacket(
                        match.gameId(),
                        playerA,
                        match.readyTimeoutSec()
                )
        );

        log.info(
                "match found gameId={} playerA={} playerB={}",
                match.gameId(),
                match.playerAId(),
                match.playerBId()
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