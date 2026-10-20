package com.buckshot.user.dto;

import com.buckshot.level.LevelPolicy;
import com.buckshot.rating.TierPolicy;
import com.buckshot.user.entity.User;

public record PlayerProfileResponse(long userId, String nickname, int wins, int losses, int rating, String tier,
                                    int level, int money) {

    /** 레벨·돈이 필요 없는 곳(상대 정보 등)용: 레벨 1, 돈 0 */
    public PlayerProfileResponse(long userId, String nickname, int wins, int losses, int rating, String tier) {
        this(userId, nickname, wins, losses, rating, tier, 1, 0);
    }

    public static PlayerProfileResponse from(User user) {
        return new PlayerProfileResponse(
                user.getId(),
                user.getNickname() == null ? "" : user.getNickname(),
                user.getWins(),
                user.getLosses(),
                user.getRating(),
                TierPolicy.of(user.getRating()),
                LevelPolicy.levelOf(user.getExp()),
                user.getMoney());
    }
}
