package com.buckshot.ranking.dto;

import java.util.List;

public record RankingResponse(List<RankingEntry> top, RankingEntry me) {
}
