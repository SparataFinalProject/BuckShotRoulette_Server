package com.buckshot.game;

import static com.buckshot.game.Shell.BLANK;
import static com.buckshot.game.Shell.LIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MagazineTest {

    @Test
    @DisplayName("장전 시 실탄·공포탄 수를 센다")
    void countsOnLoad() {
        Magazine magazine = new Magazine(List.of(LIVE, BLANK, LIVE));

        assertEquals(2, magazine.liveCount());
        assertEquals(1, magazine.blankCount());
        assertEquals(3, magazine.remaining());
    }

    @Test
    @DisplayName("꺼내는 순서는 넣은 순서 그대로 (맨 앞이 약실)")
    void popsInOrder() {
        Magazine magazine = new Magazine(List.of(BLANK, LIVE));

        assertEquals(BLANK, magazine.pop());
        assertEquals(LIVE, magazine.pop());
        assertTrue(magazine.isEmpty());
    }

    @Test
    @DisplayName("확인(peek)은 탄을 빼지 않는다")
    void peekKeepsShell() {
        Magazine magazine = new Magazine(List.of(LIVE, BLANK));

        assertEquals(LIVE, magazine.peek());
        assertEquals(LIVE, magazine.peek());
        assertEquals(2, magazine.remaining());
    }

    @Test
    @DisplayName("탄을 꺼내도 장전 시점 개수는 그대로")
    void loadCountsDoNotChange() {
        Magazine magazine = new Magazine(List.of(LIVE, BLANK));
        magazine.pop();

        assertEquals(1, magazine.liveCount());
        assertEquals(1, magazine.blankCount());
        assertEquals(1, magazine.remaining());
        assertFalse(magazine.isEmpty());
    }

    @Test
    @DisplayName("1개 남은 탄창에 pop 두번: 두 번째는 예외")
    void popsInEmpty() {
        Magazine magazine = new Magazine(List.of(LIVE));
        magazine.pop();

        assertThrows(IllegalStateException.class, magazine::pop);
        assertThrows(IllegalStateException.class, magazine::peek);
    }
}
