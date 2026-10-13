package com.buckshot.ranking.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.dto.StreakGameDetail;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.timeline.TimelineJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연승 모드 판 하나의 상세 (타임라인). 리더보드 VIEW처럼 공개: 로그인한 누구나 남의 기록도 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakGameService {

    private final StreakGameRepository gameRepository;

    public StreakGameDetail detail(long gameId) {
        StreakGame game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STREAK_GAME_NOT_FOUND));
        return StreakGameDetail.of(game, TimelineJson.read(game.getTimeline()));
    }
}
