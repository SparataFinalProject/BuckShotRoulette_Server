package com.buckshot.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 원작(클라이언트 싱글 MatchState, ShellTray.LoadSchedule, GameManager.itemsByGrant)과 같은 값인지 못 박는다.
 * 값을 바꿀 때는 클라이언트와 함께 바꿔야 하므로, 일부러 숫자를 그대로 적어 둔다.
 */
class GameRulesTest {

    @Test
    @DisplayName("체력은 시작도 최대도 6")
    void maxHp() {
        assertEquals(6, GameRules.MAX_HP);
    }

    @Test
    @DisplayName("장전표 {공포탄, 실탄}: 2/1 -> 2/2 -> 2/3 -> 3/3, 5번째부터 계속 4/4")
    void shellCompositionSchedule() {
        assertArrayEquals(new int[] { 2, 1 }, GameRules.shellComposition(0));
        assertArrayEquals(new int[] { 2, 2 }, GameRules.shellComposition(1));
        assertArrayEquals(new int[] { 2, 3 }, GameRules.shellComposition(2));
        assertArrayEquals(new int[] { 3, 3 }, GameRules.shellComposition(3));
        assertArrayEquals(new int[] { 4, 4 }, GameRules.shellComposition(4));
        assertArrayEquals(new int[] { 4, 4 }, GameRules.shellComposition(99));
    }

    @Test
    @DisplayName("아이템 지급 개수: 2 -> 3 -> 4, 이후 계속 4")
    void itemsPerGrant() {
        assertEquals(2, GameRules.itemsPerGrant(0));
        assertEquals(3, GameRules.itemsPerGrant(1));
        assertEquals(4, GameRules.itemsPerGrant(2));
        assertEquals(4, GameRules.itemsPerGrant(3));
        assertEquals(4, GameRules.itemsPerGrant(50));
    }

    @Test
    @DisplayName("장전표를 돌려받아 고쳐도 원본이 바뀌지 않는다")
    void compositionIsACopy() {
        int[] first = GameRules.shellComposition(0);
        first[0] = 99;

        assertArrayEquals(new int[] { 2, 1 }, GameRules.shellComposition(0));
    }
}
