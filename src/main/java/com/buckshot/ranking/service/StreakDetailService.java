package com.buckshot.ranking.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.dto.StreakRunDetail;
import com.buckshot.ranking.dto.StreakRunDetail.GameRow;
import com.buckshot.ranking.dto.StreakRunDetail.Overall;
import com.buckshot.ranking.dto.StreakRunDetail.RunSummary;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리더보드 VIEW. 연승 도전 하나를 자세히 보여 준다.
 * 리더보드는 공개라서 남의 기록도 볼 수 있다 (내 도전인지 확인하지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakDetailService {

    private final StreakRunRepository runRepository;
    private final StreakGameRepository gameRepository;
    private final UserRepository userRepository;

    public StreakRunDetail detail(long runId) {
        StreakRun run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STREAK_RUN_NOT_FOUND));
        User user = userRepository.findById(run.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<GameRow> games = gameRepository.findByRunIdOrderByGameIndexAsc(runId).stream()
                .map(GameRow::of)
                .toList();

        long userId = user.getId();
        Overall overall = Overall.of(
                runRepository.countByUserId(userId),
                runRepository.sumWinsByUserId(userId),
                runRepository.countByUserIdAndEndedAtIsNotNull(userId));

        return new StreakRunDetail(RunSummary.of(run, user), games, overall);
    }
}
