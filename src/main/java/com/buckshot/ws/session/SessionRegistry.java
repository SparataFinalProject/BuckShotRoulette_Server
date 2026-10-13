package com.buckshot.ws.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Component
public class SessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public boolean register(long userId, WebSocketSession session) {
        var decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        return sessions.putIfAbsent(userId, decorated) == null;
    }

    public boolean unregister(long userId, WebSocketSession session) {
        var removed = new boolean[1];
        sessions.computeIfPresent(userId, (id, current) -> {
            removed[0] = current.getId().equals(session.getId());
            return removed[0] ? null : current;
        });
        return removed[0];
    }

    public WebSocketSession find(long userId) {
        return sessions.get(userId);
    }

    /** 지금 연결된 모든 세션 (끊김 감지용 복사본) */
    public List<WebSocketSession> all() {
        return List.copyOf(sessions.values());
    }
}
