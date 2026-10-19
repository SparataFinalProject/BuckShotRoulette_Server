package com.buckshot.game.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 아이템 조건을 아이템 쪽(DealerItem)으로 옮긴 HardDealerBrain이 옮기기 전과 모든 상황에서 같은 행동을 고르는지.
 * 옮기기 전 판단은 {@link #legacy}에 그대로 복사해 두었다 (if 순서 + 바깥에서 거른 usable).
 */
class HardDealerBrainEquivalenceTest {

    private static final int MAX = 6;

    @Test
    @DisplayName("hp·탄 수·확인한 탄·쇠톱·수갑·가진 아이템의 모든 조합에서 옮기기 전과 같은 행동")
    void sameAsLegacyForEveryState() {
        HardDealerBrain brain = new HardDealerBrain();
        // 옮기기 전 AI는 원작 5종만 알았으므로 그 5종의 조합만 비교한다 (새 아이템 판단은 따로 테스트)
        Item[] items = { Item.HANDCUFFS, Item.BEER, Item.MAGNIFIER, Item.CIGARETTE, Item.SAW };
        int checked = 0;
        for (int hp = 1; hp <= MAX; hp++)
            for (int oppHp = 1; oppHp <= MAX; oppHp++)
                for (int live = 0; live <= 4; live++)
                    for (int blank = 0; blank <= 4; blank++) {
                        if (live + blank == 0) continue;
                        for (Shell known : new Shell[] { null, Shell.LIVE, Shell.BLANK }) {
                            if (known == Shell.LIVE && live == 0 || known == Shell.BLANK && blank == 0) continue;
                            for (int flags = 0; flags < 4; flags++) {
                                boolean saw = (flags & 1) != 0, cuffed = (flags & 2) != 0;
                                for (int mask = 0; mask < (1 << items.length); mask++) {
                                    Set<Item> held = EnumSet.noneOf(Item.class);
                                    for (int i = 0; i < items.length; i++) if ((mask & (1 << i)) != 0) held.add(items[i]);

                                    DealerObservation o = new DealerObservation(hp, oppHp, MAX, live, blank, known, saw, cuffed, held);
                                    String at = o.toString();
                                    assertEquals(legacy(o, held), brain.decide(o), at);
                                    checked++;
                                }
                            }
                        }
                    }
        assertEquals(true, checked == 294_912, "조합 수 " + checked);
    }

    // ───────── 옮기기 전 HardDealerBrain (그대로 복사) ─────────

    private static DealerAction legacy(DealerObservation o, Set<Item> held) {
        // 옮기기 전에는 바깥(DealerMemory.observe / DevStreakGame.observe)에서 규칙상 못 쓰는 것을 미리 걸렀다
        Set<Item> usable = EnumSet.noneOf(Item.class);
        for (Item item : held) {
            if (item == Item.SAW && o.sawActive()) continue;
            if (item == Item.HANDCUFFS && o.opponentHandcuffed()) continue;
            usable.add(item);
        }
        double p = o.liveChance();
        boolean unknown = o.knownShell() == null;
        if (o.hp() < o.maxHp() && usable.contains(Item.CIGARETTE)) return DealerAction.use(Item.CIGARETTE);
        if (o.remaining() >= 2 && !o.opponentHandcuffed() && usable.contains(Item.HANDCUFFS)) return DealerAction.use(Item.HANDCUFFS);
        if (unknown && p > 0 && p < 1 && usable.contains(Item.MAGNIFIER)) return DealerAction.use(Item.MAGNIFIER);
        if (unknown && o.remaining() >= 2 && p >= 0.4 && p <= 0.6 && usable.contains(Item.BEER)) return DealerAction.use(Item.BEER);
        if (p < 0.5) return DealerAction.SHOOT_SELF;
        if (p >= 0.5 && o.opponentHp() >= 2 && !o.sawActive() && usable.contains(Item.SAW)) return DealerAction.use(Item.SAW);
        return DealerAction.SHOOT_OPPONENT;
    }
}
