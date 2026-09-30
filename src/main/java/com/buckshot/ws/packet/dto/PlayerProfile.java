package com.buckshot.ws.packet.dto;

public record PlayerProfile(long userId, String nickname, int wins, int losses, int rating, String tier) {
}
