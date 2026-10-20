package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.streak.timeline.Timeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 연승 모드: 첫 라운드와 재장전 뒤 모두 첫 차례는 항상 사람. */
class DevStreakTurnOrderTest {

    @Test
    @DisplayName("모든 라운드의 선턴은 사람 (첫 라운드 포함)")
    void playerAlwaysFirst() {
        int reloads = 0;
        for (long seed = 1; seed <= 60; seed++) {
            Timeline t = DevStreakTimelineTest.play(seed).timeline();
            for (int i = 0; i < t.rounds().size(); i++) {
                assertEquals(Timeline.ME, t.rounds().get(i).first(), "seed " + seed + " 라운드 " + (i + 1));
                if (i > 0) reloads++;
            }
        }
        assertTrue(reloads > 0, "재장전이 한 번도 없었다");
    }
}
