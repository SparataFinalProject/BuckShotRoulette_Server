package com.buckshot.game.ai;

import com.buckshot.game.GamePlayer;
import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DealerMemoryTest {

    private final DealerMemory memory = new DealerMemory();
    private final GamePlayer dealer = new GamePlayer(-1L, "딜러");
    private final GamePlayer player = new GamePlayer(1L, "나");

    @Test
    @DisplayName("장전하면 개수를 기억하고, 돋보기 기억은 없다")
    void remembersLoadedCounts() {
        memory.onLoaded(2, 3);

        assertEquals(2, memory.liveCount());
        assertEquals(3, memory.blankCount());
        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("실탄 1, 공포탄 2가 빠지면 그만큼 준다")
    void decreasesRemovedShells() {
        memory.onLoaded(2, 3);
        memory.onShellRemoved(Shell.LIVE);
        memory.onShellRemoved(Shell.BLANK);
        memory.onShellRemoved(Shell.BLANK);

        assertEquals(1, memory.liveCount());
        assertEquals(1, memory.blankCount());
    }

    @Test
    @DisplayName("돋보기로 본 탄은 그 탄이 빠지면 잊는다")
    void forgetsPeekedShellAfterRemoval() {
        memory.onLoaded(2, 3);
        memory.onPeeked(Shell.LIVE);

        assertEquals(Shell.LIVE, memory.knownShell());

        memory.onShellRemoved(Shell.LIVE);

        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("다시 장전하면 돋보기로 본 것도 잊는다")
    void reloadForgetsPeekedShell() {
        memory.onLoaded(1, 0);
        memory.onPeeked(Shell.LIVE);

        memory.onLoaded(2, 0);

        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("개수가 0인데 그 종류가 빠져도 음수가 안 된다")
    void neverGoesNegative() {
        memory.onLoaded(1, 0);
        memory.onShellRemoved(Shell.BLANK);

        assertEquals(1, memory.liveCount());
        assertEquals(0, memory.blankCount());
    }

    @Test
    @DisplayName("딜러·상대 hp, 기억한 탄 개수, 돋보기로 본 탄이 관찰에 그대로 들어간다")
    void observeCombinesMemoryAndPlayers() {
        dealer.takeDamage(2);
        memory.onLoaded(2, 3);
        memory.onPeeked(Shell.LIVE);

        DealerObservation observation = memory.observe(dealer, player, true);

        assertEquals(GameRules.MAX_HP, observation.opponentHp());
        assertEquals(GameRules.MAX_HP - 2, observation.hp());
        assertEquals(5, observation.remaining());
        assertEquals(2, observation.liveCount());
        assertEquals(3, observation.blankCount());
        assertEquals(Shell.LIVE, observation.knownShell());
    }

    @Test
    @DisplayName("딜러가 가진 아이템이 usable에 들어간다 (상대 아이템은 안 들어간다)")
    void observeListsOwnedItems() {

        dealer.addItem(Item.SAW);
        dealer.addItem(Item.BEER);
        player.addItem(Item.CIGARETTE);

        memory.onLoaded(2, 3);

        DealerObservation observation = memory.observe(dealer, player, false);

        assertTrue(observation.canUse(Item.SAW));
        assertTrue(observation.canUse(Item.BEER));
        assertFalse(observation.canUse(Item.CIGARETTE));
    }

    @Test
    @DisplayName("쇠톱이 켜져 있으면 쇠톱은 usable에서 빠진다")
    void observeExcludesSawWhenActive() {
        dealer.addItem(Item.SAW);

        assertFalse(memory.observe(dealer, player, true).canUse(Item.SAW));
        assertTrue(memory.observe(dealer, player, false).canUse(Item.SAW));
    }

    @Test
    @DisplayName("상대가 이미 수갑을 찼으면 수갑은 빠진다")
    void observeExcludesHandcuffsOnCuffedOpponent() {
        dealer.addItem(Item.HANDCUFFS);

        DealerObservation free = memory.observe(dealer, player, false);
        player.addHandcuff();
        DealerObservation cuffed = memory.observe(dealer, player, false);

        assertFalse(free.opponentHandcuffed());
        assertTrue(free.canUse(Item.HANDCUFFS));
        assertTrue(cuffed.opponentHandcuffed());
        assertFalse(cuffed.canUse(Item.HANDCUFFS));
    }
}
