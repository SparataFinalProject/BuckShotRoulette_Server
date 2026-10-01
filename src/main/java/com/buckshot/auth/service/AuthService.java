package com.buckshot.auth.service;

import com.buckshot.auth.dto.LoginResponse;
import com.buckshot.auth.jwt.JwtProvider;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final int initialRating;

    public AuthService(UserRepository userRepository, JwtProvider jwtProvider,
            @Value("${app.rating.initial}") int initialRating) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.initialRating = initialRating;
    }

    public LoginResponse guestLogin(String guestKey) {
        String hash = sha256(guestKey);
        User user = userRepository.findByGuestKeyHash(hash).orElseGet(() -> createUser(hash));
        JwtProvider.IssuedToken token = jwtProvider.issue(user.getId());
        return new LoginResponse(
                token.value(),
                token.expiresAt(),
                user.getId(),
                user.getNickname() == null ? "" : user.getNickname(),
                user.getNickname() == null);
    }

    private User createUser(String hash) {
        try {
            return userRepository.saveAndFlush(new User(hash, initialRating));
        } catch (DataIntegrityViolationException concurrentlyCreated) {
            return userRepository.findByGuestKeyHash(hash).orElseThrow(() -> concurrentlyCreated);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
