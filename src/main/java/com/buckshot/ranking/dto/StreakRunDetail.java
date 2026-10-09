package com.buckshot.ranking.dto;

import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 리더보드 VIEW: 연승 도전 하나의 세부정보.
 *
 * @param run     그 도전의 합계 (이긴 판만)
 * @param games   판별 기록 (진 판 포함, 순서대로)
 * @param overall 이 유저의 연승 모드 전체 통계
 */
public record StreakRunDetail(RunSummary run, List<GameRow> games, Overall overall) {

    public record RunSummary(long runId, long userId, String nickname,
                             int wins, int turns, double turnsPerWin, double accuracy, int damageTaken,
                             LocalDateTime startedAt, LocalDateTime endedAt,
                             Map<Item, Integer> itemUsage) {

        public static RunSummary of(StreakRun run, User user) {
            return new RunSummary(
                    run.getId(), user.getId(), user.getNickname() == null ? "" : user.getNickname(),
                    run.getWins(), run.getTurns(),
                    StatMath.ratio(run.getTurns(), run.getWins()),
                    StatMath.percent(run.getCorrectShots(), run.getShots()),
                    run.getDamageTaken(),
                    run.getStartedAt(), run.getEndedAt(),
                    StatMath.allItems(run.getItemUsage()));
        }
    }

    public record GameRow(int gameIndex, GameResult result, int turns, int hpLeft, int damageTaken,
                          double accuracy, Map<Item, Integer> itemUsage, LocalDateTime endedAt) {

        public static GameRow of(StreakGame game) {
            return new GameRow(
                    game.getGameIndex(), game.getResult(), game.getTurns(), game.getHpLeft(),
                    game.getDamageTaken(),
                    StatMath.percent(game.getCorrectShots(), game.getShots()),
                    StatMath.allItems(game.getItemUsage()),
                    game.getEndedAt());
        }
    }

    /**
     * @param runs    도전 횟수
     * @param games   총 판 수 (이긴 판 + 진 판)
     * @param winRate 판 승률 (%)
     */
    public record Overall(long runs, long games, long wins, double winRate) {

        public static Overall of(long runs, long wins, long losses) {
            long games = wins + losses;
            return new Overall(runs, games, wins, StatMath.percent(wins, games));
        }
    }
}
