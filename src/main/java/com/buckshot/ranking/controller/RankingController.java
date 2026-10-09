package com.buckshot.ranking.controller;

import com.buckshot.auth.web.AuthInterceptor;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.ranking.dto.StreakRunDetail;
import com.buckshot.ranking.service.RankingService;
import com.buckshot.ranking.service.StreakDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final StreakDetailService streakDetailService;

    /** 리더보드 탭 (상위 50명 + 내 순위). */
    @GetMapping
    public RankingResponse ranking(
            @RequestParam RankingType type,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return rankingService.ranking(type, userId);
    }

    /** 연승 탭의 VIEW: 도전 하나의 세부정보. runId는 연승 탭 응답의 streak.runId. */
    @GetMapping("/streak/runs/{runId}")
    public StreakRunDetail streakRun(@PathVariable long runId) {
        return streakDetailService.detail(runId);
    }
}
