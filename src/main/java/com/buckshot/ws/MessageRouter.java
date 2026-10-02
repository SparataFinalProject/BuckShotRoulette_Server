package com.buckshot.ws;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.s2c.ErrorPacket;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class MessageRouter {

    private final Map<String, PacketHandler<?>> handlers;
    private final ObjectMapper objectMapper;
    private final PacketSender packetSender;

    public MessageRouter(List<PacketHandler<?>> handlers, ObjectMapper objectMapper, PacketSender packetSender) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(PacketHandler::type, Function.identity()));
        this.objectMapper = objectMapper;
        this.packetSender = packetSender;
    }

    public void route(WsContext ctx, String payload) {
        String type = "";
        try {
            JsonNode root = objectMapper.readTree(payload);
            type = root.path("type").asString();

            PacketHandler<?> handler = handlers.get(type);
            if (handler == null) {
                sendError(ctx, ErrorCode.UNKNOWN_TYPE, type);
                return;
            }
            invoke(handler, ctx, root.path("data"));
        } catch (JacksonException e) {
            sendError(ctx, ErrorCode.INVALID_MESSAGE, type);
        } catch (BusinessException e) {
            sendError(ctx, e.getErrorCode(), type);
        } catch (Exception e) {
            log.error("handler failed userId={} type={}", ctx.userId(), type, e);
            sendError(ctx, ErrorCode.INTERNAL_ERROR, type);
        }
    }

    private <T> void invoke(PacketHandler<T> handler, WsContext ctx, JsonNode data) {
        handler.handle(ctx, objectMapper.treeToValue(data, handler.dataType()));
    }

    private void sendError(WsContext ctx, ErrorCode code, String requestType) {
        packetSender.sendTo(ctx.userId(), PacketType.ERROR, new ErrorPacket(code.name(), code.getMessage(), requestType));
    }
}
