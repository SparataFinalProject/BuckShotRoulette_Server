package com.buckshot.ws;

import com.buckshot.ws.routing.MessageRouter;
import com.buckshot.ws.session.SessionRegistry;
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
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId != null && sessionRegistry.unregister(userId, session)) {
            log.info("disconnected userId={} code={}", userId, status.getCode());
            eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
        }
    }

    private static long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
    }
}
