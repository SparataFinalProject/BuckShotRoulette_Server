package com.buckshot.auth.web;

import com.buckshot.auth.jwt.JwtProvider;
import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "auth.userId";
    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        request.setAttribute(ATTR_USER_ID, jwtProvider.parseUserId(header.substring(BEARER.length())));
        return true;
    }
}
