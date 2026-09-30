package com.buckshot.ws.packet.dto;

public record PlayerResult(long userId, int wins, int losses, int ratingDelta, int rating) {
}
