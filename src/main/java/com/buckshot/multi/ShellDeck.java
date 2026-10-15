package com.buckshot.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 원작 mp_main의 셸 묶음 뽑기 (MAIN_sequence_batch_array_for_2/3/4, MP_RoundManager.GenerateRandomSequence).
 * 인원수별 묶음 {실탄, 공포탄} 중 하나를 무작위로 뽑고, 다 뽑을 때까지 같은 묶음이 다시 나오지 않으며 비면 다시 채운다.
 * 원작 기본 설정은 모든 라운드, 모든 장전이 "-1 = 이 묶음에서 무작위"라 라운드마다 다르지 않고, 덱은 게임 내내 이어진다.
 * 4인용의 (3, 4)가 두 번 있는 것도 원작 그대로다.
 */
final class ShellDeck {

    static final Map<Integer, int[][]> BATCHES = Map.of(
            2, new int[][] {{1, 2}, {2, 1}, {2, 2}, {3, 2}, {1, 1}, {2, 3}, {3, 3}, {3, 1}, {4, 2}},
            3, new int[][] {{2, 3}, {3, 2}, {3, 3}, {4, 3}, {2, 2}, {3, 4}, {4, 4}, {4, 2}, {3, 1}, {1, 1}},
            4, new int[][] {{3, 4}, {3, 2}, {3, 3}, {4, 3}, {2, 2}, {3, 4}, {4, 4}, {4, 2}, {3, 1}, {2, 1}});

    /** 한 번 뽑은 결과: visible은 장치에 보이는 순서(실탄이 앞), inGun은 총 속의 실제 순서. true = 실탄 */
    record Draw(boolean[] visible, boolean[] inGun) {
    }

    private final int[][] table;
    private final List<int[]> pool = new ArrayList<>();
    private final Random random;

    ShellDeck(int players, Random random) {
        this.table = BATCHES.get(Math.max(2, Math.min(4, players)));
        this.random = random;
    }

    Draw draw() {
        if (pool.isEmpty()) pool.addAll(Arrays.asList(table));
        int[] batch = pool.remove(random.nextInt(pool.size()));
        boolean[] visible = new boolean[batch[0] + batch[1]];
        for (int i = 0; i < batch[0]; i++) visible[i] = true;
        List<Boolean> shuffled = new ArrayList<>();
        for (boolean live : visible) shuffled.add(live);
        Collections.shuffle(shuffled, random);
        boolean[] inGun = new boolean[visible.length];
        for (int i = 0; i < inGun.length; i++) inGun[i] = shuffled.get(i);
        return new Draw(visible, inGun);
    }
}
