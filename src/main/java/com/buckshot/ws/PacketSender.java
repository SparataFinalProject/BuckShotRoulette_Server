package com.buckshot.ws;

import com.buckshot.ws.packet.Packet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PacketSender {

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void sendTo(long userId, String type, Object data) {
        WebSocketSession session = sessionRegistry.find(userId);
        if (session == null || !session.isOpen()) {
            log.debug("send skipped, not connected userId={} type={}", userId, type);
            return;
        }

        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new Packet<>(type, data))));
        } catch (Exception e) {
            log.warn("send failed userId={} type={}: {}", userId, type, e.getMessage());
        }
    }
}
