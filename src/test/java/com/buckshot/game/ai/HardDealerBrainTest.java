package com.buckshot.game.ai;

import static com.buckshot.game.Item.BEER;
import static com.buckshot.game.Item.CIGARETTE;
import static com.buckshot.game.Item.HANDCUFFS;
import static com.buckshot.game.Item.MAGNIFIER;
import static com.buckshot.game.Item.SAW;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HardDealerBrainTest {

    private static final int MAX = 6;

    private final HardDealerBrain brain = new HardDealerBrain();

    @Test
    @DisplayName("다쳤으면 담배부터")
    void healsWhenHurt() {
        DealerObservation o = obs(MAX - 1, MAX, 1, 1, null, CIGARETTE, SAW, MAGNIFIER);

        assertEquals(DealerAction.use(CIGARETTE), brain.decide(o));
    }

    @Test
    @DisplayName("탄이 2발 이상이고 상대가 수갑을 안 찼으면 수갑")
    void handcuffs() {
        assertEquals(DealerAction.use(HANDCUFFS), brain.decide(obs(MAX, MAX, 1, 1, null, HANDCUFFS)));
    }

    @Test
    @DisplayName("반반이고 모르면 돋보기")
    void magnifierWhenUnsure() {
        assertEquals(DealerAction.use(MAGNIFIER), brain.decide(obs(MAX, MAX, 1, 1, null, MAGNIFIER)));
    }

    @Test
    @DisplayName("반반이고 돋보기가 없으면 맥주")
    void beerOnCoinFlip() {
        assertEquals(DealerAction.use(BEER), brain.decide(obs(MAX, MAX, 2, 2, null, BEER)));
    }

    @Test
    @DisplayName("공포탄 쪽이면 자신을 쏜다")
    void shootsSelfOnLikelyBlank() {
        assertEquals(DealerAction.SHOOT_SELF, brain.decide(obs(MAX, MAX, 1, 3, null)));
    }

    @Test
    @DisplayName("실탄 쪽이면 쇠톱을 켜고 상대를 쏜다")
    void sawThenShootOpponent() {
        assertEquals(DealerAction.use(SAW), brain.decide(obs(MAX, MAX, 3, 1, null, SAW)));
        assertEquals(DealerAction.SHOOT_OPPONENT, brain.decide(obs(MAX, MAX, 3, 1, null)));
    }

    @Test
    @DisplayName("돋보기로 실탄을 봤으면 개수와 상관없이 상대를 쏜다")
    void trustsMagnifier() {
        assertEquals(DealerAction.SHOOT_OPPONENT, brain.decide(obs(MAX, MAX, 1, 5, Shell.LIVE)));
    }

    @Test
    @DisplayName("고른 아이템은 항상 쓸 수 있는 것 중 하나")
    void onlyChoosesUsableItems() {
        for (Item item : Item.values()) {
            for (int live = 0; live <= 4; live++) {
                for (int blank = 0; blank <= 4; blank++) {
                    if (live + blank == 0) {
                        continue;
                    }
                    DealerObservation o = obs(MAX - 1, 2, live, blank, null, item);
                    DealerAction action = brain.decide(o);
                    if (action.kind() == DealerAction.Kind.USE_ITEM) {
                        assertEquals(item, action.item(), o.toString());
                    }
                }
            }
        }
    }

    private static DealerObservation obs(int hp, int opponentHp, int live, int blank, Shell known, Item... usable) {
        Set<Item> items = EnumSet.noneOf(Item.class);
        items.addAll(List.of(usable));
        return new DealerObservation(hp, opponentHp, MAX, live, blank, known, false, false, items);
    }
}
