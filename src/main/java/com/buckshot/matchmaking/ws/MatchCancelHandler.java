package com.buckshot.matchmaking.ws;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.ws.PacketHandler;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.WsContext;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.c2s.MatchCancelPacket;
import com.buckshot.ws.packet.s2c.MatchCancelledPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCancelHandler
        implements PacketHandler<MatchCancelPacket> {

    private static final String USER_REQUEST = "USER_REQUEST";

    private final MatchmakingService matchmakingService;
    private final PacketSender packetSender;

    @Override
    public String type() {
        return PacketType.MATCH_CANCEL;
    }

    @Override
    public Class<MatchCancelPacket> dataType() {
        return MatchCancelPacket.class;
    }

    @Override
    public void handle(
            WsContext ctx,
            MatchCancelPacket data
    ) {
        long userId = ctx.userId();

        MatchmakingService.CancelResult result =
                matchmakingService.cancel(userId);

        if (result ==
                MatchmakingService.CancelResult.ALREADY_MATCHED) {

            log.info(
                    "match cancel ignored, already matched userId={}",
                    userId
            );

            return;
        }

        if (result ==
                MatchmakingService.CancelResult.NOT_QUEUED) {

            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        log.info(
                "match cancelled userId={} reason={}",
                userId,
                USER_REQUEST
        );

        packetSender.sendTo(
                userId,
                PacketType.MATCH_CANCELLED,
                new MatchCancelledPacket(USER_REQUEST)
        );
    }
}