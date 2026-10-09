package com.buckshot.streak.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.level.LevelPolicy;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StreakService {

    private static final int SEASON = 1;

    private final UserRepository userRepository;
    private final StreakRunRepository runRepository;
    private final StreakGameRepository gameRepository;

    @Transactional
    public StreakResult onGameEnd(long userId, GameResult result, GameSummary summary) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        StreakRun run = runRepository.findByUserIdAndEndedAtIsNull(userId)
                .orElseGet(() -> runRepository.save(new StreakRun(userId, SEASON, now)));

        int gameIndex = run.getWins() + 1;

        gameRepository.save(new StreakGame(run.getId(), gameIndex, result, summary, now));

        boolean isNewBest = false;

        if (result == GameResult.WIN) {
            run.recordWin(summary);
            isNewBest = user.offerBest(run, now);
        } else {
            run.end(now);
        }

        int streak = (result == GameResult.WIN) ? run.getWins() : 0;
        int expGained = LevelPolicy.streakExp(result, streak, summary.turns());

        user.gainExp(expGained);

        return new StreakResult(streak, user.getBestStreak(), isNewBest, expGained);
    }

    /** 이번 판 시작 전 현재 연승 (진행 중인 도전의 wins, 없으면 0). */
    @Transactional(readOnly = true)
    public int currentStreak(long userId) {
        return runRepository.findByUserIdAndEndedAtIsNull(userId).map(StreakRun::getWins).orElse(0);
    }
}
