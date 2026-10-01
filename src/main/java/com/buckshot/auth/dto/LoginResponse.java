package com.buckshot.auth.dto;

public record LoginResponse(String accessToken, long expiresAt, long userId, String nickname, boolean nicknameRequired) {
}
