package com.buckshot.ws;

import com.buckshot.auth.jwt.JwtProvider;
import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "ws.userId";
    public static final String ATTR_REJECT = "ws.reject";
    private static final String BEARER = "Bearer ";
    private static final String VERSION_HEADER = "X-Protocol-Version";
    private static final String PROTOCOL_VERSION = "1";

    private final JwtProvider jwtProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        HttpHeaders headers = request.getHeaders();
        if (!PROTOCOL_VERSION.equals(headers.getFirst(VERSION_HEADER))) {
            attributes.put(ATTR_REJECT, CloseCodes.VERSION_MISMATCH);
            return true;
        }

        String header = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            attributes.put(ATTR_REJECT, CloseCodes.AUTH_FAILED);
            return true;
        }

        try {
            attributes.put(ATTR_USER_ID, jwtProvider.parseUserId(header.substring(BEARER.length())));
        } catch (BusinessException e) {
            attributes.put(ATTR_REJECT, e.getErrorCode() == ErrorCode.AUTH_EXPIRED
                    ? CloseCodes.AUTH_EXPIRED : CloseCodes.AUTH_FAILED);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
