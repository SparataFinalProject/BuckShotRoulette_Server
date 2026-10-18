package com.buckshot.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    @Test
    @DisplayName("레이팅이 같으면 승자 +16 (K=32의 절반)")
    void equalRatings() {
        assertEquals(16, EloCalculator.winnerGain(1000, 1000));
    }

    @Test
    @DisplayName("약한 쪽이 이기면 더 많이 얻는다 (400 차이면 +29)")
    void underdogWinsGainsMore() {
        // 기대 승률 1/(1+10^1) = 0.0909 -> 32 * 0.9091 = 29.09
        assertEquals(29, EloCalculator.winnerGain(1000, 1400));
    }

    @Test
    @DisplayName("강한 쪽이 이기면 더 적게 얻는다 (400 차이면 +3)")
    void favoriteWinsGainsLess() {
        // 기대 승률 0.9091 -> 32 * 0.0909 = 2.91
        assertEquals(3, EloCalculator.winnerGain(1400, 1000));
    }

    @Test
    @DisplayName("200 차이: 약한 쪽이 이기면 +24, 강한 쪽이 이기면 +8")
    void twoHundredGap() {
        // 기대 승률 0.2403 / 0.7597
        assertEquals(24, EloCalculator.winnerGain(1000, 1200));
        assertEquals(8, EloCalculator.winnerGain(1200, 1000));
    }

    @Test
    @DisplayName("얻는 점수는 항상 0 이상 K 이하")
    void gainStaysWithinBounds() {
        for (int diff = -3000; diff <= 3000; diff += 50) {
            int gain = EloCalculator.winnerGain(1000, 1000 + diff);
            assertTrue(gain >= 0 && gain <= EloCalculator.K, "diff=" + diff + " gain=" + gain);
        }
    }
}
