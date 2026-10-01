package com.buckshot.auth.jwt;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration ttl;

    public JwtProvider(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public IssuedToken issue(long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt.toEpochMilli());
    }

    public long parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Long.parseLong(subject);
        } catch (ExpiredJwtException expired) {
            throw new BusinessException(ErrorCode.AUTH_EXPIRED);
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
    }

    public record IssuedToken(String value, long expiresAt) {
    }
}
