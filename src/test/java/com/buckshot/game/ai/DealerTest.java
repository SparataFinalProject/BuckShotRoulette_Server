package com.buckshot.game.ai;

import com.buckshot.game.GamePlayer;
import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DealerTest {

    private DealerObservation seen;
    private final DealerAction answer = DealerAction.use(Item.BEER);

    /**
     * 판단은 하지 않고, 받은 관찰값을 저장하고 정해진 답만 돌려주는 가짜 brain.
     */
    private final Dealer dealer = new Dealer(observation -> {
        seen = observation;
        return answer;
    });

    private final GamePlayer me = new GamePlayer(-1L, "딜러");
    private final GamePlayer opponent = new GamePlayer(1L, "나");

    @Test
    @DisplayName("slotOf는 목록 순서가 아니라 아이템이 들어 있는 칸 번호를 돌려준다")
    void slotOfReturnsSlotNumber() {
        int sawSlot = me.addItem(Item.SAW);    // 0번 칸
        int beerSlot = me.addItem(Item.BEER);  // 1번 칸
        me.removeItem(sawSlot);                // 0번 칸 비움 → [ (1, BEER) ]

        assertEquals(beerSlot, dealer.slotOf(me, Item.BEER));
    }

    @Test
    @DisplayName("없는 아이템을 찾으면 IllegalStateException")
    void slotOfThrowsWhenMissing() {
        me.addItem(Item.SAW);   // 다른 아이템은 있지만 찾는 것은 없음

        assertThrows(IllegalStateException.class, () -> dealer.slotOf(me, Item.MAGNIFIER));
    }

    @Test
    @DisplayName("장전·빠진 탄·돋보기 이벤트가 memory를 거쳐 brain까지 전달된다")
    void decidePassesMemoryToBrain() {
        dealer.onLoaded(2, 3);
        dealer.onShellRemoved(Shell.LIVE);
        dealer.onPeeked(Shell.BLANK);

        dealer.decide(me, opponent, false);

        assertEquals(1, seen.liveCount());
        assertEquals(3, seen.blankCount());
        assertEquals(Shell.BLANK, seen.knownShell());
    }

    @Test
    @DisplayName("딜러·상대 hp, 톱 상태, 딜러 아이템이 brain에 그대로 전달된다")
    void decidePassesPlayersAndSaw() {
        me.takeDamage(2);
        opponent.takeDamage(1);
        me.addItem(Item.CIGARETTE);
        dealer.onLoaded(1, 1);

        dealer.decide(me, opponent, true);

        assertEquals(GameRules.MAX_HP - 2, seen.hp());
        assertEquals(GameRules.MAX_HP - 1, seen.opponentHp());
        assertTrue(seen.sawActive());
        assertTrue(seen.canUse(Item.CIGARETTE));

        dealer.decide(me, opponent, false);
        assertFalse(seen.sawActive());
    }

    @Test
    @DisplayName("brain이 고른 행동을 그대로 돌려준다")
    void decideReturnsBrainAction() {
        dealer.onLoaded(1, 1);

        assertSame(answer, dealer.decide(me, opponent, false));
    }
}
