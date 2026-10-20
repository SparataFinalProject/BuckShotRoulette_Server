package com.buckshot.streak.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.level.LevelPolicy;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.reward.StreakRewards;
import com.buckshot.streak.StreakSeason;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.streak.timeline.Timeline;
import com.buckshot.streak.timeline.TimelineJson;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StreakService {


    private final UserRepository userRepository;
    private final StreakRunRepository runRepository;
    private final StreakGameRepository gameRepository;

    /**
     * 판이 끝났을 때 (GAME_OVER) 한 트랜잭션으로 판 기록(타임라인 포함)·연승·최고 기록·경험치를 저장한다.
     *
     * @param timeline 판별 타임라인. 없으면 null
     */
    @Transactional
    public StreakResult onGameEnd(long userId, GameResult result, GameSummary summary, Timeline timeline) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        StreakRun run = currentSeasonRun(userId, now)
                .orElseGet(() -> runRepository.save(new StreakRun(userId, StreakSeason.CURRENT.number(), now)));

        int gameIndex = run.getWins() + 1;

        gameRepository.save(new StreakGame(run.getId(), run.getSeason(), gameIndex, result, summary,
                TimelineJson.write(timeline), now));

        boolean isNewBest = false;

        if (result == GameResult.WIN) {
            run.recordWin(summary);
            isNewBest = user.offerBest(run, now);
        } else {
            run.end(now);
        }

        int streak = (result == GameResult.WIN) ? run.getWins() : 0;
        int expGained = LevelPolicy.streakExp(result, streak, summary.turns());

        int moneyGained = StreakRewards.cash(result, streak, summary.turns());

        user.gainExp(expGained);
        user.gainMoney(moneyGained);

        return new StreakResult(streak, user.getBestStreak(), isNewBest, expGained, moneyGained);
    }

    /** 이번 판 시작 전 현재 연승 (진행 중인 도전의 wins, 없으면 0). 지난 시즌에 시작한 도전은 이어지지 않으므로 0. */
    @Transactional(readOnly = true)
    public int currentStreak(long userId) {
        return runRepository.findByUserIdAndEndedAtIsNull(userId)
                .filter(run -> run.getSeason() == StreakSeason.CURRENT.number())
                .map(StreakRun::getWins).orElse(0);
    }

    /**
     * 진행 중인 도전. 시즌이 바뀌었으면 지난 시즌에 시작한 도전은 끝내고(그 시즌 기록으로 남는다) 없는 것으로 본다:
     * 새 시즌 아이템으로 하는 판은 새 시즌 도전으로 센다.
     */
    private java.util.Optional<StreakRun> currentSeasonRun(long userId, LocalDateTime now) {
        java.util.Optional<StreakRun> open = runRepository.findByUserIdAndEndedAtIsNull(userId);
        if (open.isPresent() && open.get().getSeason() != StreakSeason.CURRENT.number()) {
            open.get().end(now);
            return java.util.Optional.empty();
        }
        return open;
    }
}
