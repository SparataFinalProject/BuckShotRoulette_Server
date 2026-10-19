package com.buckshot.ranking.dto;

import java.util.List;

/** @param season 연승 탭이면 지금 시즌 정보, 다른 탭이면 null */
public record RankingResponse(List<RankingEntry> top, RankingEntry me, SeasonInfo season) {

    public RankingResponse(List<RankingEntry> top, RankingEntry me) {
        this(top, me, null);
    }
}
