package com.buckshot.ranking.dto;

import com.buckshot.level.LevelPolicy;
import com.buckshot.rating.TierPolicy;
import com.buckshot.user.entity.User;

/**
 * 리더보드 한 줄.
 *
 * @param rank   순위. 동점이면 같은 순위 (1, 2, 2, 4). 순위가 없으면 null (연승 기록 없음)
 * @param score  탭마다 다름 (레이팅 탭 rating, 레벨 탭 exp, 연승 탭 최고 연승)
 * @param streak 연승 탭에서만 채워짐. 다른 탭은 null
 */
public record RankingEntry(Integer rank, long userId, String nickname, String tier, int level, int score,
                           StreakStats streak) {

    public static RankingEntry of(User user, Integer rank, int score) {
        return of(user, rank, score, null);
    }

    public static RankingEntry of(User user, Integer rank, int score, StreakStats streak) {
        return new RankingEntry(
                rank,
                user.getId(),
                user.getNickname() == null ? "" : user.getNickname(),
                TierPolicy.of(user.getRating()),
                LevelPolicy.levelOf(user.getExp()),
                score,
                streak);
    }
}
