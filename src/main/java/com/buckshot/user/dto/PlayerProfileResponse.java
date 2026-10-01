package com.buckshot.user.dto;

import com.buckshot.rating.TierPolicy;
import com.buckshot.user.entity.User;

public record PlayerProfileResponse(long userId, String nickname, int wins, int losses, int rating, String tier) {

    public static PlayerProfileResponse from(User user) {
        return new PlayerProfileResponse(
                user.getId(),
                user.getNickname() == null ? "" : user.getNickname(),
                user.getWins(),
                user.getLosses(),
                user.getRating(),
                TierPolicy.of(user.getRating()));
    }
}
