package com.buckshot.ws;

import com.buckshot.matchmaking.service.MatchmakingService;
import com.buckshot.ws.routing.MessageRouter;
import com.buckshot.ws.session.SessionRegistry;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.s2c.MatchCancelledPacket;
import com.buckshot.ws.session.UserDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final MessageRouter messageRouter;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchmakingService matchmakingService;
    private final PacketSender packetSender;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var reject = (CloseStatus) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_REJECT);
        if (reject != null) {
            log.info("rejected session={} code={}", session.getId(), reject.getCode());
            session.close(reject);
            return;
        }

        long userId = userId(session);
        if (!sessionRegistry.register(userId, session)) {
            log.info("duplicate connection userId={}", userId);
            session.close(CloseCodes.DUPLICATE_CONNECTION);
            return;
        }
        log.info("connected userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        messageRouter.route(new WsContext(userId(session)), message.getPayload());
    }


    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        Long userId = (Long) session.getAttributes()
                .get(AuthHandshakeInterceptor.ATTR_USER_ID);

        if (userId == null ||
                !sessionRegistry.unregister(userId, session)) {
            return;
        }

        // 1. 매칭 대기 중 연결 종료
        boolean removedFromQueue =
                matchmakingService.removeFromQueue(userId);

        if (removedFromQueue) {
            log.info(
                    "match queue removed on disconnect userId={}",
                    userId
            );
        }

        // 2. MATCH_FOUND 이후 준비 중 연결 종료
        MatchmakingService.DisconnectedMatch disconnectedMatch =
                matchmakingService.removePendingMatchOnDisconnect(userId);

        if (disconnectedMatch != null) {
            log.info(
                    "pending match cancelled gameId={} disconnectedUserId={} opponentUserId={}",
                    disconnectedMatch.gameId(),
                    userId,
                    disconnectedMatch.opponentUserId()
            );

            // 상대방 알림 전송이 실패해도 아래 이벤트 발행은 계속 진행
            try {
                packetSender.sendTo(
                        disconnectedMatch.opponentUserId(),
                        PacketType.MATCH_CANCELLED,
                        new MatchCancelledPacket("OPPONENT_DISCONNECTED")
                );
            } catch (Exception e) {
                log.error(
                        "failed to notify opponent gameId={} opponentUserId={}",
                        disconnectedMatch.gameId(),
                        disconnectedMatch.opponentUserId(),
                        e
                );
            }
        }

        // 3. 연결 종료 로그
        log.info(
                "disconnected userId={} code={}",
                userId,
                status.getCode()
        );

        // 4. dev 브랜치의 연결 종료 이벤트 발행
        eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
    }


    private static long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
    }
}
