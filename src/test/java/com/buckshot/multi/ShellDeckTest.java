package com.buckshot.multi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShellDeckTest {

    private static int[] counts(ShellDeck.Draw d) {
        int live = 0;
        for (boolean b : d.visible()) if (b) live++;
        return new int[] {live, d.visible().length - live};
    }

    @Test
    @DisplayName("원작 묶음 표: 인원별 개수와 총 셸 수 범위")
    void tablesMatchOriginal() {
        assertEquals(9, ShellDeck.BATCHES.get(2).length);
        assertEquals(10, ShellDeck.BATCHES.get(3).length);
        assertEquals(10, ShellDeck.BATCHES.get(4).length);
        for (int[][] table : ShellDeck.BATCHES.values())
            for (int[] b : table) assertTrue(b[0] + b[1] >= 2 && b[0] + b[1] <= 8);
    }

    @Test
    @DisplayName("한 바퀴 동안은 표의 묶음을 하나씩 모두 뽑고, 다 뽑으면 다시 채운다")
    void drawsWithoutRepeatUntilEmpty() {
        for (int players = 3; players <= 4; players++) {
            ShellDeck deck = new ShellDeck(players, new Random(players));
            for (int lap = 0; lap < 2; lap++) {
                List<String> drawn = new ArrayList<>();
                for (int i = 0; i < 10; i++) drawn.add(Arrays.toString(counts(deck.draw())));
                List<String> expected = new ArrayList<>();
                for (int[] b : ShellDeck.BATCHES.get(players)) expected.add(Arrays.toString(b));
                drawn.sort(null);
                expected.sort(null);
                assertEquals(expected, drawn);
            }
        }
    }

    @Test
    @DisplayName("보이는 순서는 실탄이 앞이고, 총 속 순서는 같은 개수를 섞은 것이다")
    void visibleIsSortedAndGunIsShuffled() {
        ShellDeck deck = new ShellDeck(4, new Random(1));
        boolean shuffledOnce = false;
        for (int i = 0; i < 30; i++) {
            ShellDeck.Draw d = deck.draw();
            int live = counts(d)[0];
            for (int k = 0; k < d.visible().length; k++) assertEquals(k < live, d.visible()[k]);
            int gunLive = 0;
            for (boolean b : d.inGun()) if (b) gunLive++;
            assertEquals(live, gunLive);
            if (!Arrays.equals(d.visible(), d.inGun())) shuffledOnce = true;
        }
        assertTrue(shuffledOnce);
    }

    @Test
    @DisplayName("같은 시드면 같은 순서가 나온다")
    void deterministicWithSeed() {
        assertArrayEquals(new ShellDeck(3, new Random(7)).draw().visible(), new ShellDeck(3, new Random(7)).draw().visible());
    }
}
