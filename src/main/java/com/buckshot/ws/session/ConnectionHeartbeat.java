package com.buckshot.ws.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 끊김 감지 (ServerDesign 6.6, S9): 10초마다 모든 연결에 WebSocket ping을 보내고, 20초 동안 아무 응답(pong이나 패킷)이 없으면 연결을 닫는다.
 * 와이파이 끊김·절전처럼 close 없이 사라진 연결(half-open)을 서버가 모르는 채 두면, 게임 상대가 그 턴을 끝없이 기다리고
 * 같은 계정의 재접속이 중복 접속(4002)으로 거절된다. 닫으면 afterConnectionClosed → UserDisconnectedEvent로 평소 끊김 처리를 탄다.
 * Unity ClientWebSocket은 ping에 pong을 자동으로 답하므로 클라이언트는 고칠 것이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionHeartbeat {

    private static final long PING_INTERVAL_MS = 10_000;
    private static final long TIMEOUT_MS = 20_000;
    private static final String ATTR_LAST_SEEN = "lastSeenAt";

    private final SessionRegistry sessionRegistry;

    /** 연결 직후와 클라이언트에게서 무엇이든(pong, 패킷) 받을 때마다 부른다 */
    public static void touch(WebSocketSession session) {
        session.getAttributes().put(ATTR_LAST_SEEN, System.currentTimeMillis());
    }

    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    public void tick() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : sessionRegistry.all()) {
            Long lastSeen = (Long) session.getAttributes().get(ATTR_LAST_SEEN);
            if (lastSeen != null && now - lastSeen > TIMEOUT_MS) {
                log.info("heartbeat timeout session={} silentMs={}", session.getId(), now - lastSeen);
                close(session);
                continue;
            }
            try {
                session.sendMessage(new PingMessage());
            } catch (Exception e) {
                log.debug("ping failed session={}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private static void close(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (Exception e) {
            log.debug("close failed session={}: {}", session.getId(), e.getMessage());
        }
    }
}
