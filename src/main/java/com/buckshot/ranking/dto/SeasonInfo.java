package com.buckshot.ranking.dto;

import com.buckshot.streak.StreakSeason;
import java.util.List;

/**
 * 연승 시즌 정보 (리더보드 연승 탭에 같이 내려 준다).
 *
 * @param number 시즌 번호 (1부터)
 * @param title  시즌 이름 (예: "체력회복 X")
 * @param items  그 시즌에 나오는 아이템 (ItemType 이름)
 */
public record SeasonInfo(int number, String title, List<String> items) {

    public static SeasonInfo of(StreakSeason season) {
        return new SeasonInfo(season.number(), season.title(), season.items());
    }
}
