package com.buckshot.ranking.controller;

import com.buckshot.auth.web.AuthInterceptor;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public RankingResponse ranking(
            @RequestParam RankingType type,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return rankingService.ranking(type, userId);
    }
}
