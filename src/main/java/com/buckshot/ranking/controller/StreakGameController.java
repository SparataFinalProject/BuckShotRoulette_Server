package com.buckshot.ranking.controller;

import com.buckshot.ranking.dto.StreakGameDetail;
import com.buckshot.ranking.service.StreakGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 연승 모드 판별 타임라인. 로그인 토큰 필요 (AuthInterceptor, /api/**), 남의 기록도 조회 가능. */
@RestController
@RequestMapping("/api/streak/games")
@RequiredArgsConstructor
public class StreakGameController {

    private final StreakGameService streakGameService;

    /** 판 상세: 요약 + 계산된 라운드별 타임라인. gameId는 VIEW 응답 games[].gameId. */
    @GetMapping("/{gameId}")
    public StreakGameDetail detail(@PathVariable long gameId) {
        return streakGameService.detail(gameId);
    }
}
