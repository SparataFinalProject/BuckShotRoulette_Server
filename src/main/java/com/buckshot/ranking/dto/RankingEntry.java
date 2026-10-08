package com.buckshot.ranking.dto;

import com.buckshot.level.LevelPolicy;
import com.buckshot.rating.TierPolicy;
import com.buckshot.user.entity.User;

public record RankingEntry(Integer rank, long userId, String nickname, String tier, int level, int score) {
    public static RankingEntry of(User user, Integer rank, int score){
        return new RankingEntry(
                rank,
                user.getId(),
                user.getNickname() == null ? "" : user.getNickname(),
                TierPolicy.of(user.getRating()),
                LevelPolicy.levelOf(user.getExp()),
                score
        );
    }
}
